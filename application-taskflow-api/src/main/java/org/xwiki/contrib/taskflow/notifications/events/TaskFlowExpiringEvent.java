/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.contrib.taskflow.notifications.events;

import java.util.Map;
import java.util.Set;

/**
 * Task Expiring event. Notify responsible user about the reminding time to complete the task.
 *
 * @version $Id$
 * @since 2.0
 */
public class TaskFlowExpiringEvent extends AbstractTaskFlowEvent
{
    /**
     * Constructs a {@code TaskFlowExpiringEvent} with the specified target users and task parameters.
     *
     * @param target a set of user identifiers to whom the event is targeted
     * @param taskEventParams a map containing parameters related to the task event, such as task ID, title, or deadline
     */
    public TaskFlowExpiringEvent(Set<String> target, Map<String, String> taskEventParams)
    {
        super(target, taskEventParams);
    }

    /**
     * Constructs an empty {@code TaskFlowExpiringEvent} with no target or parameters.
     * This constructor may be used for deserialization or manual population of event data.
     */
    public TaskFlowExpiringEvent()
    {
    }

    @Override
    public boolean matches(Object otherEvent)
    {
        return otherEvent instanceof TaskFlowExpiringEvent;
    }
}
