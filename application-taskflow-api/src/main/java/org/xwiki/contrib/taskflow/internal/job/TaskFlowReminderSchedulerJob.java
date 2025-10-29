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
package org.xwiki.contrib.taskflow.internal.job;

import java.util.List;
import java.util.Map;

import com.xpn.xwiki.plugin.scheduler.AbstractJob;
import com.xpn.xwiki.web.Utils;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xwiki.contrib.taskflow.TaskFlowManager;
import org.xwiki.model.reference.DocumentReference;

/**
 * /** Scheduled job that alerts responsible users about their pending tasks.
 * <p>
 * This job runs every hour to ensure timely notifications are sent based on each task's configured reminder intervals.
 * Supported intervals include: 1 hour, 2 hours, 4 hours, 8 hours, 12 hours, 1 day, 2 days, and 5 days.
 *
 * <p>
 * Note that the "Job execution context user" property of this scheduler JOB is set to XWiki.XWikiGuest This done on
 * purpose in order to make sure that notifications will be sent regardless of configuration of the filters : "System
 * Filter" and "Own Events Filter". For example, if the job execution context user is set to superadmin the notification
 * will not be sent when the "System Filter" filter is enabled, so, initializing the job context user with the guest
 * user will ensure that the notifications will not be hidden.
 *
 * @version $Id$
 * @since 2.0
 */
public class TaskReminderSchedulerJob extends AbstractJob implements Job
{
    @Override
    protected void executeJob(JobExecutionContext jobContext) throws JobExecutionException
    {
        Logger logger = LoggerFactory.getLogger(TaskReminderSchedulerJob.class);
        TaskFlowManager taskFlowManager = Utils.getComponent(TaskFlowManager.class);
        logger.debug("Task Reminder Scheduler Job started ...");
        // interval → user → document → list of task IDs
        Map<String, Map<DocumentReference, Map<DocumentReference, List<String>>>> tasksToRemindMap =
            taskFlowManager.getTasksToRemind();
        // Send one notification/email per event interval → user → document → list of task IDs.
        for (Map.Entry<String, Map<DocumentReference,
            Map<DocumentReference, List<String>>>> tasksToRemindEntry : tasksToRemindMap.entrySet()) {
            String interval = tasksToRemindEntry.getKey();
            Map<DocumentReference, Map<DocumentReference, List<String>>> taskUserMap = tasksToRemindEntry.getValue();
            for (Map.Entry<DocumentReference,
                Map<DocumentReference, List<String>>> taskUserEntry : taskUserMap.entrySet()) {
                DocumentReference userRef = taskUserEntry.getKey();
                Map<DocumentReference, List<String>> taskRefMap = taskUserEntry.getValue();
                for (Map.Entry<DocumentReference, List<String>> taskRefEntry : taskRefMap.entrySet()) {
                    DocumentReference taskRef = taskRefEntry.getKey();
                    List<String> taskRids = taskRefEntry.getValue();
                    for (String taskRid : taskRids) {
                        taskFlowManager.notifyResponsibleUser(taskRef, userRef, taskRid, "taskCreator");
                    }
                }
            }
        }
        logger.debug("Task Reminder Scheduler Job finished ...");
    }
}
