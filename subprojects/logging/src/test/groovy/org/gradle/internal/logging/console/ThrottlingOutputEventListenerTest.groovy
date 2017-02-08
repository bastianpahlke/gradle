/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.internal.logging.console

import org.gradle.internal.logging.OutputSpecification
import org.gradle.internal.logging.events.EndOutputEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.OutputEventQueueDrainedEvent
import org.gradle.util.MockExecutor
import org.gradle.util.MockTimeProvider
import spock.lang.Subject

class ThrottlingOutputEventListenerTest extends OutputSpecification {
    def listener = Mock(OutputEventListener)
    def console = Mock(Console)
    def statusBar = Mock(Label)
    def timeProvider = new MockTimeProvider()
    def executor = new MockExecutor()

    @Subject renderer = new ThrottlingOutputEventListener(listener, console, 100, executor, timeProvider)

    def setup() {
        (0..1) * console.getStatusBar() >> statusBar
    }

    def forwardsEventsToListener() {
        def event = event('message')

        when:
        renderer.onOutput(event)

        then:
        1 * listener.onOutput(event)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _
    }

    def queuesEventsReceivedSoonAfterFirstAndForwardsThemLater() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')
        def event4 = event('4')

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _

        when:
        flush()

        then:
        1 * listener.onOutput(event2)
        1 * listener.onOutput(event3)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _

        when:
        renderer.onOutput(event4)

        then:
        0 * _
    }

    def forwardsEventReceivedSomeTimeAfterFirst() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')

        given:
        renderer.onOutput(event1)

        when:
        timeProvider.increment(100)
        renderer.onOutput(event2)

        then:
        1 * listener.onOutput(event2)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _

        when:
        renderer.onOutput(event3)

        then:
        0 * _
    }

    def forwardsQueuedEventsOnEndOfOutputEvent() {
        def event1 = event('1')
        def event2 = event('2')
        def event3 = event('3')
        def end = new EndOutputEvent()

        when:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(event3)

        then:
        1 * listener.onOutput(event1)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _

        when:
        renderer.onOutput(end)

        then:
        1 * listener.onOutput(event2)
        1 * listener.onOutput(event3)
        1 * listener.onOutput(end)
        1 * listener.onOutput(_ as OutputEventQueueDrainedEvent)
        1 * console.flush()
        0 * _
    }

    def backgroundFlushDoesNothingWhenEventsAlreadyFlushed() {
        def event1 = event('1')
        def event2 = event('2')
        def end = new EndOutputEvent()

        given:
        renderer.onOutput(event1)
        renderer.onOutput(event2)
        renderer.onOutput(end)

        when:
        flush()

        then:
        0 * _
    }

    void flush() {
        executor.runNow()
    }
}