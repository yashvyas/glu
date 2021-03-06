/*
 * Copyright (c) 2010-2010 LinkedIn, Inc
 * Portions Copyright (c) 2011 Yan Pujante
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package test.orchestration.engine.delta

import org.linkedin.glu.provisioner.core.model.SystemModel
import org.linkedin.glu.provisioner.core.model.SystemEntry
import org.linkedin.groovy.util.collections.GroovyCollectionsUtils
import org.linkedin.groovy.util.json.JsonUtils
import org.linkedin.glu.orchestration.engine.delta.DeltaServiceImpl
import org.linkedin.glu.orchestration.engine.delta.impl.DeltaMgrImpl
import org.linkedin.glu.orchestration.engine.delta.SystemEntryDelta.DeltaState
import org.linkedin.glu.provisioner.core.model.JSONSystemModelSerializer
import org.linkedin.glu.orchestration.engine.delta.CustomDeltaDefinition
import org.linkedin.glu.orchestration.engine.delta.CustomGroupByDelta

class TestDeltaService extends GroovyTestCase
{
  DeltaMgrImpl deltaMgr = new DeltaMgrImpl()
  DeltaServiceImpl deltaService = new DeltaServiceImpl(deltaMgr: deltaMgr)

  def DEFAULT_INCLUDED_IN_VERSION_MISMATCH = deltaMgr.includedInVersionMismatch

  void testDeltaService()
  {
    // empty
    def current = []
    def expected = []
    assertEqualsIgnoreType([], doComputeDelta(expected, current))

    // notDeployed
    current = []
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notDeployed',
                            statusInfo: 'NOT deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // notDeployed + cluster (GLU-393)
    current = []
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', cluster: 'cl1']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.cluster': 'cl1',
                            'metadata.container': 'c1',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notDeployed',
                            statusInfo: 'NOT deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // notExpectedState (with default = running)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notExpectedState',
                            statusInfo: 'running!=stopped',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // notExectedState (with specific state=stopped)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'configured',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'configured']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'configured',
                            entryState: 'stopped',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notExpectedState',
                            statusInfo: 'stopped!=configured',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // notRunning + versionMismatch => default is versionMismatch wins
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w2'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'initParameters.wars:[w2!=w1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w2'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // notRunning + versionMismatch => we force notRunning to win
    deltaService.stateDeltaOverridesDelta = true
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w2'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notExpectedState',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'initParameters.wars:[w2!=w1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w2'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // restoring defaults
    deltaService.stateDeltaOverridesDelta = false

    // versionMismatch (wars)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w2'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'initParameters.wars:[w2!=w1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w2'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // versionMismatch (config)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1', config: 'cnf1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1', config: 'cnf2'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'initParameters.config': 'cnf2',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'initParameters.config:[cnf2!=cnf1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // versionMismatch (wars & config)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'stopped',
            initParameters: [wars: 'w1', config: 'cnf1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w2', config: 'cnf2'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'initParameters.config': 'cnf2',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'initParameters.config:[cnf2!=cnf1]',
                                         'initParameters.wars:[w2!=w1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w2'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // versionMismatch (script)
    current = [
      [
        agent: 'a1', mountPoint: '/m1', script: 's1',
        entryState: 'stopped',
        initParameters: [wars: 'w1', config: 'cnf1'],
        metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
      ]
    ]
    expected = [
      [
        agent: 'a1', mountPoint: '/m1', script: 's2',
        initParameters: [wars: 'w1', config: 'cnf1'],
        metadata: [container: 'c1', product: 'p1', version: 'R2']
      ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'initParameters.config': 'cnf1',
                            'metadata.currentState': 'stopped',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's2',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: ['entryState:[running!=stopped]',
                                         'script:[s2!=s1]'],
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // versionMismatch (script) (with includedInVersionMismatch)
    withNewDeltaMgr(['script'], null ) {
      current = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's1',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      expected = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's2',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      assertEqualsIgnoreType([
                             [
                              'metadata.container': 'c1',
                              'initParameters.config': 'cnf1',
                              entryState: 'running',
                              key: 'a1:/m1',
                              agent: 'a1',
                              mountPoint: '/m1',
                              'metadata.product': 'p1',
                              script: 's2',
                              state: DeltaState.ERROR,
                              status: 'delta',
                              statusInfo: 'script:[s2!=s1]',
                              'metadata.version': 'R2',
                              'initParameters.wars': 'w1'
                              ]
                             ],
                             doComputeDelta(expected, current))
    }

    // versionMismatch (script) (with includedInVersionMismatch)
    withNewDeltaMgr(['initParameters.wars'], null) {
      current = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's1',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      expected = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's2',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      assertEqualsIgnoreType([
                             [
                              'metadata.container': 'c1',
                              'initParameters.config': 'cnf1',
                              entryState: 'running',
                              key: 'a1:/m1',
                              agent: 'a1',
                              mountPoint: '/m1',
                              'metadata.product': 'p1',
                              script: 's1',
                              state: DeltaState.OK,
                              status: 'expectedState',
                              statusInfo: 'running',
                              'metadata.version': 'R2',
                              'initParameters.wars': 'w1'
                              ]
                             ],
                             doComputeDelta(expected, current))
    }

    // versionMismatch (script) (with excludedInVersionMismatch)
    withNewDeltaMgr(DEFAULT_INCLUDED_IN_VERSION_MISMATCH, ['initParameters.wars']) {
      current = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's1',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
      ]
      expected = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's2',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'stopped']
        ]
      ]
      assertEqualsIgnoreType([
                             [
                             'metadata.container': 'c1',
                             'initParameters.config': 'cnf1',
                             'metadata.currentState': 'stopped',
                             entryState: 'running',
                             key: 'a1:/m1',
                             agent: 'a1',
                             mountPoint: '/m1',
                             'metadata.product': 'p1',
                             script: 's2',
                             state: DeltaState.ERROR,
                             status: 'delta',
                             statusInfo: 'script:[s2!=s1]',
                             'metadata.version': 'R2',
                             'initParameters.wars': 'w1'
                             ]
                             ],
                             doComputeDelta(expected, current))
    }

    // versionMismatch (script) (with excludedInVersionMismatch)
    withNewDeltaMgr(DEFAULT_INCLUDED_IN_VERSION_MISMATCH, ['script']) {
      current = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's1',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      expected = [
        [
          agent: 'a1', mountPoint: '/m1', script: 's2',
          initParameters: [wars: 'w1', config: 'cnf1'],
          metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
      ]
      assertEqualsIgnoreType([
                             [
                              'metadata.container': 'c1',
                              'initParameters.config': 'cnf1',
                              entryState: 'running',
                              key: 'a1:/m1',
                              agent: 'a1',
                              mountPoint: '/m1',
                              'metadata.product': 'p1',
                              script: 's1',
                              state: DeltaState.OK,
                              status: 'expectedState',
                              statusInfo: 'running',
                              'metadata.version': 'R2',
                              'initParameters.wars': 'w1'
                              ]
                             ],
                             doComputeDelta(expected, current))
    }

    // unexpected
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    expected = [
        [
            agent: 'a2', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]

    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'unexpected',
                            statusInfo: 'should NOT be deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ],
                           [
                            'metadata.container': 'c1',
                            entryState: 'running',
                            key: 'a2:/m1',
                            agent: 'a2',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notDeployed',
                            statusInfo: 'NOT deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // error
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'running', error: 'in error']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'running',
                            'metadata.error': 'in error',
                             error: 'in error',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'error',
                            statusInfo: 'in error',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // ok
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            entryState: 'running',
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'running'],
            tags: ['ec:1', 'ec:2']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2'],
            tags: ['ee:1']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'running',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.OK,
                            status: 'expectedState',
                            statusInfo: 'running',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1',
                            'tags.ee:1': 'a1:/m1',
                             tags: ['ee:1']
                            ]
                            ],
                           doComputeDelta(expected, current))

    // ok (with cluster)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'running',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'running']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', cluster: 'cl1', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.cluster': 'cl1',
                            'metadata.currentState': 'running',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.OK,
                            status: 'expectedState',
                            statusInfo: 'running',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // ok (with cluster)
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'running',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', cluster: 'cl1', product: 'p1', version: 'R2', currentState: 'running']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', cluster: 'cl2', product: 'p1', version: 'R2']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.cluster': 'cl1',
                            'metadata.currentState': 'running',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.OK,
                            status: 'expectedState',
                            statusInfo: 'running',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current))

    // (system) tags
    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            entryState: 'running',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'running'],
            tags: ['ec:1', 'ec:2']
        ]
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2'],
            tags: ['ee:1']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'running',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.OK,
                            status: 'expectedState',
                            statusInfo: 'running',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1',
                            'tags.ee:1': 'a1:/m1',
                            'tags.a:2': 'a1:/m1',
                             tags: ['a:2', 'ee:1']
                            ]
                           ],
                           doComputeDelta(expected, current, { SystemModel cs, SystemModel es ->
                             cs.addAgentTags('a1', ['a:1'])
                             es.addAgentTags('a1', ['a:2'])
                             [cs, es]
                           }))

    current = [
    ]
    expected = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2'],
            tags: ['ee:1']
        ]
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'notDeployed',
                            statusInfo: 'NOT deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1',
                            'tags.ee:1': 'a1:/m1',
                            'tags.a:2': 'a1:/m1',
                             tags: ['a:2', 'ee:1']
                            ]
                           ],
                           doComputeDelta(expected, current, { SystemModel cs, SystemModel es ->
                             cs.addAgentTags('a1', ['a:1'])
                             es.addAgentTags('a1', ['a:2'])
                             [cs, es]
                           }))

    current = [
        [
            agent: 'a1', mountPoint: '/m1', script: 's1',
            initParameters: [wars: 'w1'],
            metadata: [container: 'c1', product: 'p1', version: 'R2', currentState: 'running'],
            tags: ['ec:1', 'ec:2']
        ]
    ]
    expected = [
    ]
    assertEqualsIgnoreType([
                           [
                            'metadata.container': 'c1',
                            'metadata.currentState': 'running',
                            entryState: 'running',
                            key: 'a1:/m1',
                            agent: 'a1',
                            mountPoint: '/m1',
                            'metadata.product': 'p1',
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'unexpected',
                            statusInfo: 'should NOT be deployed',
                            'metadata.version': 'R2',
                            'initParameters.wars': 'w1'
                            ]
                           ],
                           doComputeDelta(expected, current, { SystemModel cs, SystemModel es ->
                             cs.addAgentTags('a1', ['a:1'])
                             es.addAgentTags('a1', ['a:2'])
                             [cs, es]
                           }))
  }

  public void testCustomGroupByDelta()
  {
    def current, expected, cdd, delta, res

    // ok
    current = [
      [
        agent: 'a1', mountPoint: '/m1', script: 's1',
        initParameters: [webapps: [[war: 'w1', contextPath: '/cp1'], [war: 'w2', contextPath: '/cp2']]],
        entryState: 'running',
        metadata: [container: 'c1', product: 'p1', version: '1.2.0', currentState: 'running', modifiedTime: 1000],
        tags: ['ec:1', 'ec:2']
      ],
      [
        agent: 'a1', mountPoint: '/m2', script: 's1',
        initParameters: [webapps: [war: 'w1', contextPath: '/cp1']],
        entryState: 'running',
        metadata: [container: 'c1', product: 'p1', version: '1.1.0', currentState: 'running', modifiedTime: 1500],
        tags: ['ec:1', 'ec:3']
      ],
      [
        agent: 'a1', mountPoint: '/m3', script: 's1',
        initParameters: [wars: 'w1'],
        entryState: 'running',
        metadata: [container: 'c2', product: 'p1', version: '1.0.0', currentState: 'running', modifiedTime: 2000],
        tags: ['ec:1', 'ec:4']
      ],
      [
        agent: 'a2', mountPoint: '/m1', script: 's1',
        initParameters: [wars: 'w1'],
        entryState: 'running',
        metadata: [container: 'c2', product: 'p1', version: '1.0.0', currentState: 'running', modifiedTime: 1000],
        tags: ['ec:1', 'ec:5']
      ]
    ]
    expected = current

    // summary = true, min / max + 2 columns with *same* source!
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'ag',
          source: 'agent',
          orderBy: 'asc'
        ],
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'vs',
          source: 'metadata.version'
        ],
        [
          name: 'oldest',
          source: 'metadata.modifiedTime',
          groupBy: 'min'
        ],
        [
          name: 'newest',
          source: 'metadata.modifiedTime',
          groupBy: 'max'
        ],
      ]
    ]

    res = """
{
  "a1": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": {"count": 3},
      "newest": 2000,
      "oldest": 1000,
      "vs": {"count": 3}
    }],
    "errorsCount": 0,
    "instancesCount": 3,
    "na": "",
    "state": "OK"
  },
  "a2": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a2",
      "mp": "/m1",
      "newest": 1000,
      "oldest": 1000,
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    delta = checkCustomGroupByDelta(res, expected, current, cdd)
    // YP Note: the json transformation looses the sort order as it always sort in an ascending
    // fashion, this is why I am doing a special test on the actual value returned to make
    // sure that the orderBy: 'asc' is taken into account on the first column!
    assertEquals(["a1", "a2"], delta.groupByDelta.keySet() as List)

    // summary = true, min / max + 2 columns with *same* source!
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'ag',
          source: 'agent',
          orderBy: 'desc'
        ],
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'vs',
          source: 'metadata.version'
        ],
        [
          name: 'oldest',
          source: 'metadata.modifiedTime',
          groupBy: 'min'
        ],
        [
          name: 'newest',
          source: 'metadata.modifiedTime',
          groupBy: 'max'
        ],
      ]
    ]

    res = """
{
  "a1": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": {"count": 3},
      "newest": 2000,
      "oldest": 1000,
      "vs": {"count": 3}
    }],
    "errorsCount": 0,
    "instancesCount": 3,
    "na": "",
    "state": "OK"
  },
  "a2": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a2",
      "mp": "/m1",
      "newest": 1000,
      "oldest": 1000,
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    delta = checkCustomGroupByDelta(res, expected, current, cdd)
    // YP Note: the json transformation looses the sort order as it always sort in an ascending
    // fashion, this is why I am doing a special test on the actual value returned to make
    // sure that the orderBy: 'desc' is taken into account on the first column!
    assertEquals(["a2", "a1"], delta.groupByDelta.keySet() as List)

    // summary = true
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'vs',
          source: 'metadata.version'
        ],
      ]
    ]

    res = """
{
  "/m1": {
    "deltasCount": 0,
    "entries": [{
      "ag": {"count": 2},
      "mp": "/m1",
      "vs": {"count": 2}
    }],
    "errorsCount": 0,
    "instancesCount": 2,
    "na": "",
    "state": "OK"
  },
  "/m2": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m2",
      "vs": "1.1.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "/m3": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m3",
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = true, groupBy = uniqueCount
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
          groupBy: 'uniqueCount'
        ],
      ]
    ]

    res = """
{
  "/m1": {
    "deltasCount": 0,
    "entries": [{
      "ag": {"count": 2},
      "mp": "/m1",
      "vs": {"count": 2}
    }],
    "errorsCount": 0,
    "instancesCount": 2,
    "na": "",
    "state": "OK"
  },
  "/m2": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m2",
      "vs": {"count": 1}
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "/m3": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m3",
      "vs": {"count": 1}
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = true, groupBy = uniqueVals
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
          groupBy: 'uniqueVals'
        ],
        [
          name: 'mp',
          source: 'mountPoint',
          groupBy: 'uniqueVals'
        ],
        [
          name: 'vs',
          source: 'metadata.version',
          groupBy: 'uniqueVals'
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [{
    "ag": [
      "a1",
      "a2"
    ],
    "mp": [
      "/m1",
      "/m2",
      "/m3"
    ],
    "src": "s1",
    "vs": [
      "1.2.0",
      "1.1.0",
      "1.0.0"
    ]
  }],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = true, groupBy = vals
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
          groupBy: 'vals'
        ],
        [
          name: 'mp',
          source: 'mountPoint',
          groupBy: 'vals'
        ],
        [
          name: 'vs',
          source: 'metadata.version',
          groupBy: 'vals'
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [{
    "ag": [
      "a1",
      "a1",
      "a1",
      "a2"
    ],
    "mp": [
      "/m1",
      "/m2",
      "/m3",
      "/m1"
    ],
    "src": "s1",
    "vs": [
      "1.2.0",
      "1.1.0",
      "1.0.0",
      "1.0.0"
    ]
  }],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = true, groupBy = count
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
          groupBy: 'count'
        ],
        [
          name: 'mp',
          source: 'mountPoint',
          groupBy: 'count'
        ],
        [
          name: 'vs',
          source: 'metadata.version',
          groupBy: 'count'
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [{
    "ag": {"count": 4},
    "mp": {"count": 4},
    "src": "s1",
    "vs": {"count": 4}
  }],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = false
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: false,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'mp',
          source: 'mountPoint',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [
    {
      "ag": "a1",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.2.0"
    },
    {
      "ag": "a1",
      "mp": "/m2",
      "src": "s1",
      "vs": "1.1.0"
    },
    {
      "ag": "a1",
      "mp": "/m3",
      "src": "s1",
      "vs": "1.0.0"
    },
    {
      "ag": "a2",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.0.0"
    }
  ],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = false, orderBy = 'desc'
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: false,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
          orderBy: 'desc'
        ],
        [
          name: 'mp',
          source: 'mountPoint',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [
    {
      "ag": "a2",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.0.0"
    },
    {
      "ag": "a1",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.2.0"
    },
    {
      "ag": "a1",
      "mp": "/m2",
      "src": "s1",
      "vs": "1.1.0"
    },
    {
      "ag": "a1",
      "mp": "/m3",
      "src": "s1",
      "vs": "1.0.0"
    }
  ],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = false, orderBy = 'desc'
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: false,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
          orderBy: 'desc'
        ],
        [
          name: 'mp',
          source: 'mountPoint',
          orderBy: 'desc'
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [
    {
      "ag": "a2",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.0.0"
    },
    {
      "ag": "a1",
      "mp": "/m3",
      "src": "s1",
      "vs": "1.0.0"
    },
    {
      "ag": "a1",
      "mp": "/m2",
      "src": "s1",
      "vs": "1.1.0"
    },
    {
      "ag": "a1",
      "mp": "/m1",
      "src": "s1",
      "vs": "1.2.0"
    }
  ],
  "errorsCount": 0,
  "instancesCount": 4,
  "na": "",
  "state": "OK"
}}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = false, errorsOnly = true
    cdd = [
      name: 'd1',
      errorsOnly: true,
      summary: false,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'mp',
          source: 'mountPoint',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
      ]
    ]

    res = "{}"

    delta = checkCustomGroupByDelta(res, expected, current, cdd)
    assertEquals(0, delta.counts['errors'])
    assertEquals(0, delta.counts['instances'])
    assertEquals(0, delta.totals['errors'])
    assertEquals(4, delta.totals['instances'])

    // now with real errors
    // summary = false, errorsOnly = true
    cdd = [
      name: 'd1',
      errorsOnly: true,
      summary: false,
      columnsDefinition: [
        [
          name: 'src',
          source: 'script',
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'mp',
          source: 'mountPoint',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
        [
          name: 'st',
          source: 'status',
        ],
      ]
    ]

    res = """
{"s1": {
  "deltasCount": 0,
  "entries": [
    {
      "ag": "a1",
      "mp": "/m1",
      "src": "s1",
      "st": "notDeployed",
      "vs": "1.2.0"
    },
    {
      "ag": "a1",
      "mp": "/m2",
      "src": "s1",
      "st": "notDeployed",
      "vs": "1.1.0"
    },
    {
      "ag": "a1",
      "mp": "/m3",
      "src": "s1",
      "st": "notDeployed",
      "vs": "1.0.0"
    },
    {
      "ag": "a2",
      "mp": "/m1",
      "src": "s1",
      "st": "notDeployed",
      "vs": "1.0.0"
    }
  ],
  "errorsCount": 4,
  "instancesCount": 4,
  "na": "",
  "state": "ERROR"
}}
"""

    delta = checkCustomGroupByDelta(res, expected, [], cdd)
    assertEquals(4, delta.counts['errors'])
    assertEquals(4, delta.counts['instances'])
    assertEquals(4, delta.totals['errors'])
    assertEquals(4, delta.totals['instances'])
    assertEquals(2, delta.counts['ag'])
    assertEquals(3, delta.counts['mp'])

    // summary = false, using tags (not first column)
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: false,
      columnsDefinition: [
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'ts',
          source: 'tags',
          groupBy: 'uniqueVals'
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
      ]
    ]

    res = """
{
  "/m1": {
    "deltasCount": 0,
    "entries": [
      {
        "ag": "a1",
        "mp": "/m1",
        "ts": [
          "ec:1",
          "ec:2"
        ],
        "vs": "1.2.0"
      },
      {
        "ag": "a2",
        "mp": "/m1",
        "ts": [
          "ec:1",
          "ec:5"
        ],
        "vs": "1.0.0"
      }
    ],
    "errorsCount": 0,
    "instancesCount": 2,
    "na": "",
    "state": "OK"
  },
  "/m2": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m2",
      "ts": [
        "ec:1",
        "ec:3"
      ],
      "vs": "1.1.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "/m3": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m3",
      "ts": [
        "ec:1",
        "ec:4"
      ],
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)

    // summary = false, using tags (first column)
    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'ts0',
          source: 'tags',
          groupBy: 'uniqueVals'
        ],
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'ag',
          source: 'agent',
        ],
        [
          name: 'vs',
          source: 'metadata.version',
        ],
        [
          name: 'ts1',
          source: 'tags',
          groupBy: 'uniqueVals'
        ],
      ]
    ]

    res = """
{
  "[ec:1]": {
    "deltasCount": 0,
    "entries": [{
      "ag": {"count": 2},
      "mp": {"count": 3},
      "ts0": ["ec:1"],
      "ts1": [
        "ec:1",
        "ec:2",
        "ec:3",
        "ec:4",
        "ec:5"
      ],
      "vs": {"count": 3}
    }],
    "errorsCount": 0,
    "instancesCount": 4,
    "na": "",
    "state": "OK"
  },
  "[ec:2]": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m1",
      "ts0": ["ec:2"],
      "ts1": [
        "ec:1",
        "ec:2"
      ],
      "vs": "1.2.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "[ec:3]": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m2",
      "ts0": ["ec:3"],
      "ts1": [
        "ec:1",
        "ec:3"
      ],
      "vs": "1.1.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "[ec:4]": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a1",
      "mp": "/m3",
      "ts0": ["ec:4"],
      "ts1": [
        "ec:1",
        "ec:4"
      ],
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  },
  "[ec:5]": {
    "deltasCount": 0,
    "entries": [{
      "ag": "a2",
      "mp": "/m1",
      "ts0": ["ec:5"],
      "ts1": [
        "ec:1",
        "ec:5"
      ],
      "vs": "1.0.0"
    }],
    "errorsCount": 0,
    "instancesCount": 1,
    "na": "",
    "state": "OK"
  }
}
"""
    checkCustomGroupByDelta(res, expected, current, cdd)
  }

  public void testGroupBy()
  {
    def cdd, expected

    cdd = [
      name: 'd1',
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'ag',
          source: 'agent',
          orderBy: 'asc'
        ],
        [
          name: 'mp',
          source: 'mountPoint'
        ],
        [
          name: 'vs',
          source: 'metadata.version'
        ],
        [
          name: 'oldest',
          source: 'metadata.modifiedTime',
          groupBy: 'min'
        ],
        [
          name: 'newest',
          source: 'metadata.modifiedTime',
          groupBy: 'max'
        ],
      ]
    ]

    CustomDeltaDefinition definition = CustomDeltaDefinition.fromExternalRepresentation(cdd)

    definition = definition.groupBy("newest")

    expected = [
      name: 'd1',
      description: null,
      customFilter: null,
      errorsOnly: false,
      summary: true,
      columnsDefinition: [
        [
          name: 'newest',
          source: 'metadata.modifiedTime',
          groupBy: 'max',
          orderBy: 'asc',
          linkable: true,
          visible: true
        ],
        [
          name: 'ag',
          source: 'agent',
          groupBy: 'uniqueCountOrUniqueVal',
          orderBy: 'asc',
          linkable: true,
          visible: true
        ],
        [
          name: 'mp',
          source: 'mountPoint',
          groupBy: 'uniqueCountOrUniqueVal',
          orderBy: 'asc',
          linkable: true,
          visible: true
        ],
        [
          name: 'vs',
          source: 'metadata.version',
          groupBy: 'uniqueCountOrUniqueVal',
          orderBy: 'asc',
          linkable: true,
          visible: true
        ],
        [
          name: 'oldest',
          source: 'metadata.modifiedTime',
          groupBy: 'min',
          orderBy: 'asc',
          linkable: true,
          visible: true
        ],
      ]
    ]

    assertTrue(GroovyCollectionsUtils.compareIgnoreType(expected,
                                                        definition.toExternalRepresentation()))
  }

  public void testEmptyAgent()
  {
    def current
    def expected

    // nothing deployed on the agent at all
    current = [
      [
        agent: 'a1', entryState: 'NA', metadata: [emptyAgent: true, currentState: 'NA']
      ]
    ]
    expected = [
    ]

    assertEqualsIgnoreType([
                           [
                            'metadata.currentState': 'NA',
                            'metadata.emptyAgent': true,
                            entryState: 'NA',
                            key: 'a1:null',
                            agent: 'a1',
                            state: DeltaState.NA,
                            status: 'NA',
                            statusInfo: 'empty agent'
                            ]
                           ],
                           doComputeDelta(expected, current))

    current = [
      [
        agent: 'a1', entryState: 'NA', metadata: [emptyAgent: true, currentState: 'NA']
      ]
    ]
    expected = [
    ]

    assertEqualsIgnoreType([
                           [
                            'metadata.currentState': 'NA',
                            'metadata.emptyAgent': true,
                            entryState: 'NA',
                            key: 'a1:null',
                            agent: 'a1',
                            state: DeltaState.NA,
                            status: 'NA',
                            statusInfo: 'empty agent',
                            tags: ['a:2'],
                             "tags.a:2": "a1:null"
                            ]
                            ],
                           doComputeDelta(expected, current, { SystemModel cs, SystemModel es ->
                             cs.addAgentTags('a1', ['a:2'])
                             [cs, es]
                           }))
  }

  /**
   * Specific tests for parent/child relationship
   */
  void testParentChild()
  {
    def current
    def expected

    // parentDelta (parent needs redeploy => child needs redeploy)
    current = [
      [
        agent: 'a1', mountPoint: '/p1', script: 's1'
      ],
      [
        agent: 'a1', mountPoint: '/c1', script: 's1', parent: '/p1'
      ]
    ]
    expected = [
      [
        agent: 'a1', mountPoint: '/p1', script: 's2'
      ],
      [
        agent: 'a1', mountPoint: '/c1', script: 's1', parent: '/p1'
      ]
    ]
    assertEqualsIgnoreType([
                           [
                            entryState: 'running',
                            key: 'a1:/c1',
                            agent: 'a1',
                            mountPoint: '/c1',
                            parent: "/p1",
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'parentDelta',
                            statusInfo: 'needs redeploy (parent delta)'
                           ],
                           [
                            entryState: 'running',
                            key: 'a1:/p1',
                            agent: 'a1',
                            mountPoint: '/p1',
                            script: 's2',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: 'script:[s2!=s1]'
                           ],
                           ],
                           doComputeDelta(expected, current))

    // when the filter, filters out the child, it still need to be present otherwise the plan
    // will be wrong!
    assertEqualsIgnoreType([
                           [
                            entryState: 'running',
                            key: 'a1:/c1',
                            agent: 'a1',
                            mountPoint: '/c1',
                            parent: "/p1",
                            script: 's1',
                            state: DeltaState.ERROR,
                            status: 'parentDelta',
                            statusInfo: 'needs redeploy (parent delta)'
                           ],
                           [
                            entryState: 'running',
                            key: 'a1:/p1',
                            agent: 'a1',
                            mountPoint: '/p1',
                            script: 's2',
                            state: DeltaState.ERROR,
                            status: 'delta',
                            statusInfo: 'script:[s2!=s1]'
                           ],
                           ],
                           deltaF(current, expected, "mountPoint='/p1'"))
  }

  // Testing for use case where metadata changes (version in this case)
  // entry | current | expected
  // e1    | null    | null
  // e2    | null    | R1016
  // e3    | null    | R1036
  // e4    | R1016   | null
  // e5    | R1016   | R1016
  // e6    | R1016   | R1036
  // e7    | R1036   | null
  // e8    | R1036   | R1016
  // e9    | R1036   | R1036
  def static CURRENT =
    [
        'm1': null, 'm2': null, 'm3': null,
        'm4': 'R1016', 'm5': 'R1016', 'm6': 'R1016',
        'm7': 'R1036', 'm8': 'R1036', 'm9': 'R1036'
    ]

  // building expected
  def static EXPECTED =
    [
        'm1': null, 'm2': 'R1016', 'm3': 'R1036',
        'm4': null, 'm5': 'R1016', 'm6': 'R1036',
        'm7': null, 'm8': 'R1016', 'm9': 'R1036'
    ]

  public void testMetadataChanges()
  {
    // Testing for use case where metadata changes (version in this case)
    // e1 | null  | null
    // e2 | null  | R1016
    // e3 | null  | R1036
    // e4 | R1016 | null
    // e5 | R1016 | R1016
    // e6 | R1016 | R1036
    // e7 | R1036 | null
    // e8 | R1036 | R1016
    // e9 | R1036 | R1036

    // full system computeDelta (everything up and running)
    def currentSystem = toSystem(CURRENT, 'running')
    def expectedSystem = toSystem(EXPECTED, null)
    doDeltaAndCheck(currentSystem, expectedSystem, CURRENT.keySet(), 'running')

    // full system computeDelta (everything stopped)
    currentSystem = toSystem(CURRENT, 'stopped')
    doDeltaAndCheck(currentSystem, expectedSystem, CURRENT.keySet(), 'stopped')

    // expectedSystem filtered by R1036 (everything up and running)
    currentSystem = toSystem(CURRENT, 'running')
    expectedSystem = expectedSystem.filterByMetadata('version', 'R1036')

    def expectedMountPoints = new TreeSet()
    expectedMountPoints.addAll(CURRENT.findAll { k,v -> v == 'R1036'}.collect { k,v -> k })
    expectedMountPoints.addAll(EXPECTED.findAll { k,v -> v == 'R1036'}.collect { k,v -> k })

    doDeltaAndCheck(currentSystem, expectedSystem, expectedMountPoints, 'running')

    // expectedSystem filtered by R1036 (everything stopped)
    currentSystem = toSystem(CURRENT, 'stopped')
    doDeltaAndCheck(currentSystem, expectedSystem, expectedMountPoints, 'stopped')
  }

  private void doDeltaAndCheck(SystemModel currentSystem,
                               SystemModel expectedSystem,
                               def expectedMountPoints,
                               String state)
  {
    def expectedDelta = []

    expectedMountPoints.each { mountPoint ->
      def entry =
      [
          agent: 'a1',
          entryState: 'running',
          mountPoint: "/${mountPoint}".toString(),
          script: 's1',
          'initParameters.wars': 'w1',
          'metadata.currentState': state,
          key: "a1:/${mountPoint}".toString(),
          status: state == 'running' ? 'expectedState' : 'notExpectedState',
          statusInfo: state == 'running' ? 'running' : 'running!=stopped',
          state: state == 'running' ? DeltaState.OK : DeltaState.ERROR
      ]

      // when not in error, then priority comes from 'current'
      if(state == 'running')
      {
        if(CURRENT[mountPoint])
          entry['metadata.version'] = CURRENT[mountPoint]
        else
        {
          if(EXPECTED[mountPoint])
            entry['metadata.version'] = EXPECTED[mountPoint]
        }
      }
      else
      {
        // when in error, then priority comes from 'expected'
        if(EXPECTED[mountPoint])
          entry['metadata.version'] = EXPECTED[mountPoint]
        else
        {
          if(CURRENT[mountPoint])
            entry['metadata.version'] = CURRENT[mountPoint]
        }
      }

      expectedDelta << entry
    }

    assertEqualsIgnoreType(expectedDelta, deltaService.computeDelta(expectedSystem, currentSystem))
  }

  public void testComputeDeltaAsJSON()
  {
    String delta

    SystemModel expectedModel =
      m([agent: 'a1', mountPoint: '/m0', script: 's1'],
        [agent: 'a1', mountPoint: '/m1', script: 's1'],
        [agent: 'a1', mountPoint: '/m2', script: 's2'],
        [agent: 'a1', mountPoint: '/m3', script: 's2', initParameters: [ip1: "iv1"]])

    SystemModel currentModel =
      m([agent: 'a1', mountPoint: '/m0', script: 's1'],
        [agent: 'a1', mountPoint: '/m2', script: 's1'],
        [agent: 'a1', mountPoint: '/m3', script: 's1', initParameters: [ip1: "iv2"]])

    delta = jsonDelta(expectedModel, currentModel, [:])

    assertEquals("""{
  "delta": {
    "a1:/m0": {
      "agent": "a1",
      "entryState": "running",
      "key": "a1:/m0",
      "mountPoint": "/m0",
      "script": "s1",
      "state": "OK",
      "status": "expectedState",
      "statusInfo": "running"
    },
    "a1:/m1": {
      "agent": {
        "ev": "a1"
      },
      "entryState": {
        "ev": "running"
      },
      "errorValueKeys": [
        "entryState",
        "script"
      ],
      "key": {
        "ev": "a1:/m1"
      },
      "mountPoint": {
        "ev": "/m1"
      },
      "script": {
        "ev": "s1"
      },
      "state": "ERROR",
      "status": "notDeployed",
      "statusInfo": "NOT deployed"
    },
    "a1:/m2": {
      "agent": "a1",
      "entryState": "running",
      "errorValueKeys": [
        "script"
      ],
      "key": "a1:/m2",
      "mountPoint": "/m2",
      "script": {
        "cv": "s1",
        "ev": "s2"
      },
      "state": "ERROR",
      "status": "delta",
      "statusInfo": "script:[s2!=s1]"
    },
    "a1:/m3": {
      "agent": "a1",
      "entryState": "running",
      "errorValueKeys": [
        "initParameters.ip1",
        "script"
      ],
      "initParameters.ip1": {
        "cv": "iv2",
        "ev": "iv1"
      },
      "key": "a1:/m3",
      "mountPoint": "/m3",
      "script": {
        "cv": "s1",
        "ev": "s2"
      },
      "state": "ERROR",
      "status": "delta",
      "statusInfo": [
        "initParameters.ip1:[iv1!=iv2]",
        "script:[s2!=s1]"
      ]
    }
  }
}""", delta)

    delta = jsonDelta(expectedModel, currentModel, [errorsOnly: true])

    assertEquals("""{
  "delta": {
    "a1:/m1": {
      "agent": {
        "ev": "a1"
      },
      "entryState": {
        "ev": "running"
      },
      "errorValueKeys": [
        "entryState",
        "script"
      ],
      "key": {
        "ev": "a1:/m1"
      },
      "mountPoint": {
        "ev": "/m1"
      },
      "script": {
        "ev": "s1"
      },
      "state": "ERROR",
      "status": "notDeployed",
      "statusInfo": "NOT deployed"
    },
    "a1:/m2": {
      "agent": "a1",
      "entryState": "running",
      "errorValueKeys": [
        "script"
      ],
      "key": "a1:/m2",
      "mountPoint": "/m2",
      "script": {
        "cv": "s1",
        "ev": "s2"
      },
      "state": "ERROR",
      "status": "delta",
      "statusInfo": "script:[s2!=s1]"
    },
    "a1:/m3": {
      "agent": "a1",
      "entryState": "running",
      "errorValueKeys": [
        "initParameters.ip1",
        "script"
      ],
      "initParameters.ip1": {
        "cv": "iv2",
        "ev": "iv1"
      },
      "key": "a1:/m3",
      "mountPoint": "/m3",
      "script": {
        "cv": "s1",
        "ev": "s2"
      },
      "state": "ERROR",
      "status": "delta",
      "statusInfo": [
        "initParameters.ip1:[iv1!=iv2]",
        "script:[s2!=s1]"
      ]
    }
  }
}""", delta)

    delta = jsonDelta(expectedModel, currentModel, [flatten: true])

    assertEquals("""{
  "delta": {
    "a1:/m0": {
      "agent": "a1",
      "entryState": "running",
      "key": "a1:/m0",
      "mountPoint": "/m0",
      "script": "s1",
      "state": "OK",
      "status": "expectedState",
      "statusInfo": "running"
    },
    "a1:/m1": {
      "agent": "a1",
      "entryState": "running",
      "key": "a1:/m1",
      "mountPoint": "/m1",
      "script": "s1",
      "state": "ERROR",
      "status": "notDeployed",
      "statusInfo": "NOT deployed"
    },
    "a1:/m2": {
      "agent": "a1",
      "entryState": "running",
      "key": "a1:/m2",
      "mountPoint": "/m2",
      "script": "s2",
      "state": "ERROR",
      "status": "delta",
      "statusInfo": "script:[s2!=s1]"
    },
    "a1:/m3": {
      "agent": "a1",
      "entryState": "running",
      "initParameters.ip1": "iv1",
      "key": "a1:/m3",
      "mountPoint": "/m3",
      "script": "s2",
      "state": "ERROR",
      "status": "delta",
      "statusInfo": [
        "initParameters.ip1:[iv1!=iv2]",
        "script:[s2!=s1]"
      ]
    }
  }
}""", delta)
  }

  public void testParentChildDeltaWithFilter()
  {
    String expectedModelString = """{
  "agentTags": {"agent-1": ["a:tag1"]},
  "entries": [
    {
      "agent": "agent-1",
      "mountPoint": "/c1",
      "parent": "/p1",
      "script": "file:/tmp/ChildGluScript.groovy",
      "tags": []
    },
    {
      "agent": "agent-1",
      "mountPoint": "/c2",
      "parent": "/p1",
      "script": "file:/tmp/ChildGluScript.groovy",
      "tags": []
    },
    {
      "agent": "agent-1",
      "initParameters": {"foo": "bar"},
      "mountPoint": "/p1",
      "script": "file:/tmp/ParentGluScript.groovy",
      "tags": []
    },
    {
      "agent": "agent-1",
      "mountPoint": "/p2",
      "script": "file:/tmp/ParentGluScript.groovy",
      "tags": []
    }
  ],
  "fabric": "glu-dev-1",
  "id": "9583ae021e15c99d0d59ab93e807f281c20d3699",
  "metadata": {"product": {
    "product1": {
      "name": "product1",
      "version": "1.0.0"
    },
    "product2": {
      "name": "product2",
      "version": "2.0.0"
    }
  }}
}"""

    String currentModelString = """{
  "entries": [
    {
      "agent": "agent-1",
      "entryState": "running",
      "metadata": {
        "currentState": "running",
        "modifiedTime": 1308414678502,
        "scriptState": {
          "script": {},
          "stateMachine": {"currentState": "running"}
        }
      },
      "mountPoint": "/c1",
      "parent": "/p1",
      "script": "file:/tmp/ChildGluScript.groovy",
      "tags": ["a:tag1"]
    },
    {
      "agent": "agent-1",
      "entryState": "running",
      "metadata": {
        "currentState": "running",
        "modifiedTime": 1308414612227,
        "scriptState": {
          "script": {},
          "stateMachine": {"currentState": "running"}
        }
      },
      "mountPoint": "/c2",
      "parent": "/p2",
      "script": "file:/tmp/ChildGluScript.groovy",
      "tags": ["a:tag1"]
    },
    {
      "agent": "agent-1",
      "entryState": "running",
      "metadata": {
        "currentState": "running",
        "modifiedTime": 1308414678275,
        "scriptState": {
          "script": {},
          "stateMachine": {"currentState": "running"}
        }
      },
      "mountPoint": "/p1",
      "script": "file:/tmp/ParentGluScript.groovy",
      "tags": ["a:tag1"]
    },
    {
      "agent": "agent-1",
      "entryState": "running",
      "metadata": {
        "currentState": "running",
        "modifiedTime": 1308414612061,
        "scriptState": {
          "script": {},
          "stateMachine": {"currentState": "running"}
        }
      },
      "mountPoint": "/p2",
      "script": "file:/tmp/ParentGluScript.groovy",
      "tags": ["a:tag1"]
    }
  ],
  "fabric": "glu-dev-1",
  "metadata": {"accuracy": "ACCURATE"}
}"""

    String filter = "mountPoint='/c1'"

    SystemModel expectedModel = JSONSystemModelSerializer.INSTANCE.deserialize(expectedModelString)
    SystemModel currentModel = JSONSystemModelSerializer.INSTANCE.deserialize(currentModelString)


    def delta = deltaMgr.computeDelta(expectedModel.filterBy(filter),
                                      currentModel,
                                      null)

    assertTrue(delta.hasErrorDelta())
  }

  private SystemModel toSystem(Map system, String currentState)
  {
    def entries = []

    system.each { mountPoint, version ->

      def entry =
      [
          agent: 'a1', mountPoint: "/${mountPoint}".toString(), script: 's1',
          initParameters: [wars: 'w1'],
          metadata: [:]
      ]

      if(currentState)
      {
        entry.metadata.currentState = currentState
        entry.entryState = currentState
      }

      if(version)
        entry.metadata.version = version

      entries << entry
    }

    return toSystem(entries)
  }

  private def deltaF(def current, def expected, def filter)
  {
    doComputeDelta(expected, current, null) { SystemModel cs, SystemModel es ->
      [cs, es.filterBy(filter)]
    }
  }

  private def doComputeDelta(def expected, def current)
  {
    doComputeDelta(expected, current, null, null)
  }

  private def doComputeDelta(def expected, def current, Closure closure)
  {
    doComputeDelta(expected, current, closure, null)
  }

  private def doComputeDelta(def expected, def current, Closure beforeEntries, Closure afterEntries)
  {
    SystemModel currentSystem = createEmptySystem(current)
    SystemModel expectedSystem = createEmptySystem(expected)

    if(beforeEntries)
      (currentSystem, expectedSystem) = beforeEntries(currentSystem, expectedSystem)

    addEntries(currentSystem, current)
    addEntries(expectedSystem, expected)

    if(afterEntries)
      (currentSystem, expectedSystem) = afterEntries(currentSystem, expectedSystem)

    return deltaService.computeDelta(expectedSystem, currentSystem)
  }

  private SystemModel toSystem(def system)
  {
    SystemModel res = createEmptySystem(system)
    addEntries(res, system)
    return res
  }

  private SystemModel createEmptySystem(def system)
  {
    system != null ? new SystemModel(fabric: 'f1') : null
  }

  private void addEntries(SystemModel model, def entries)
  {
    entries?.each { e ->
      model.addEntry(SystemEntry.fromExternalRepresentation(e))
    }
  }

  private void withNewDeltaMgr(def includedInVersionMismatch,
                               def excludedInVersionMismatch,
                               Closure closure)
  {
    def oldi = deltaMgr.includedInVersionMismatch
    def olde = deltaMgr.excludedInVersionMismatch
    deltaMgr.includedInVersionMismatch = includedInVersionMismatch as Set
    deltaMgr.excludedInVersionMismatch = excludedInVersionMismatch as Set
    try
    {
      closure()
    }
    finally
    {
      deltaMgr.excludedInVersionMismatch = olde
      deltaMgr.includedInVersionMismatch = oldi
    }
  }
  
  /**
   * Convenient call to compare and ignore type
   */
  void assertEqualsIgnoreType(o1, o2)
  {
    assertEquals(JsonUtils.prettyPrint(o1), JsonUtils.prettyPrint(o2))
    assertTrue("expected <${o1}> but was <${o2}>", GroovyCollectionsUtils.compareIgnoreType(o1, o2))
  }

  private String jsonDelta(SystemModel expectedModel, SystemModel currentModel, params)
  {
    params.expectedModel = expectedModel
    params.currentModel = currentModel
    params.prettyPrint = true

    deltaService.computeDeltaAsJSON(params)
  }

  private SystemModel m(Map... entries)
  {
    SystemModel model = new SystemModel(fabric: "f1")

    entries.each {
      model.addEntry(SystemEntry.fromExternalRepresentation(it))
    }

    return model
  }

  private CustomGroupByDelta doComputeCustomGrouByDelta(def expected,
                                                        def current,
                                                        def customDeltaDefinition)
  {
    CustomDeltaDefinition cdd =
      CustomDeltaDefinition.fromExternalRepresentation(customDeltaDefinition)

    SystemModel currentModel = createEmptySystem(current)
    SystemModel expectedModel = createEmptySystem(expected)

    addEntries(currentModel, current)
    addEntries(expectedModel, expected)

    deltaService.computeCustomGroupByDelta(expectedModel, currentModel, cdd)
  }

  private CustomGroupByDelta checkCustomGroupByDelta(String expectedResult,
                                                     def expected,
                                                     def current,
                                                     def customDeltaDefinition)
  {
    CustomGroupByDelta delta = doComputeCustomGrouByDelta(expected, current, customDeltaDefinition)

    // YP note: delta.groupByDelta is a sorted map => JsonUtils.prettyPrint will not change
    // the order of the map (which depend on the comparator which in some cases can be descending)
    // this is why I am copying groupByDelta into a plain old map so that the assertEquals below
    // always work...
    String computedResult = JsonUtils.prettyPrint(new HashMap(delta.groupByDelta))

    assertEquals(JsonUtils.prettyPrint(JsonUtils.fromJSON(expectedResult)), computedResult)

    return delta
  }
}
