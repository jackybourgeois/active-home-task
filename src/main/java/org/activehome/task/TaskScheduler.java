package org.activehome.task;

/*
 * #%L
 * Active Home :: Task
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 org.activehome
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */


import com.eclipsesource.json.JsonObject;
import org.activehome.com.Request;
import org.activehome.com.Response;
import org.activehome.com.ScheduledRequest;
import org.activehome.service.RequestHandler;
import org.activehome.service.Service;
import org.kevoree.annotation.ComponentType;
import org.kevoree.annotation.Input;
import org.kevoree.annotation.Output;
import org.kevoree.annotation.Start;
import org.kevoree.log.Log;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Jacky Bourgeois
 * @version %I%, %G%
 */
@ComponentType
public class TaskScheduler extends Service implements RequestHandler {

    @Output
    private org.kevoree.api.Port toExecute;
    @Output
    private org.kevoree.api.Port pushResponse;

    private ScheduledThreadPoolExecutor stpe;
    private HashMap<UUID, Task> taskMap;
    private HashMap<String, Long> sequenceMap;

    /**
     * @param str
     */
    @Input
    public final void toSchedule(final String str) {
        JsonObject json = JsonObject.readFrom(str);
        if (json.get("execTime") != null) {
            ScheduledRequest sr = new ScheduledRequest(json.asObject());
            ScheduledFuture sf = null;
            if (!stpe.isShutdown()) {
                long convertedExecTime = (sr.getExecTime() - getCurrentTime()) / getTic().getZip();
                sf = stpe.schedule(() -> manage(sr),
                        convertedExecTime, TimeUnit.MILLISECONDS);
            }
            taskMap.put(sr.getId(), new Task(sr, sf));
            sendResponse(sr, true);
        } else {
            Request req = new Request(json);
            if (req.getMethod().compareTo("cancel")
                    == 0 && req.getParams().length == 1
                    && req.getParams()[0] instanceof String) {
                sendResponse(req, cancel(UUID.fromString((String) req.getParams()[0])));
            } else if (req.getMethod().compareTo("cancelAllFrom")
                    == 0 && req.getParams().length == 2
                    && req.getParams()[0] instanceof String) {
                sendResponse(req, cancelAllFromTo(
                        (String) req.getParams()[0], (String) req.getParams()[1]));
            }
        }
    }

    /**
     * Cancel all scheduled requests of sender/receiver
     *
     * @param src  sender
     * @param dest receiver
     * @return
     */
    private boolean cancelAllFromTo(final String src,
                                    final String dest) {
        taskMap.values().stream()
                .filter(task -> task.getRequest().getSrc().compareTo(src) == 0
                        && task.getRequest().getDest().compareTo(dest) == 0)
                .forEach(taskMap::remove);
        LinkedList<UUID> toCancel = taskMap.keySet().stream()
                .filter(key -> taskMap.get(key).getRequest().getSrc().compareTo(src) == 0
                        && taskMap.get(key).getRequest().getDest().compareTo(dest) == 0)
                .collect(Collectors.toCollection(LinkedList::new));

        toCancel.forEach(this::cancel);
        return true;
    }

    @Start
    public final void onInit() {
        initExecutor();
        taskMap = new HashMap<>();
        sequenceMap = new HashMap<>();
    }

    @Override
    public final void onPauseTime() {
        stpe.shutdownNow();
    }

    @Override
    public final void onResumeTime() {
        initExecutor();
        taskMap.values().stream().forEach(task -> {
            long convertedExecTime = (task.getRequest().getExecTime() - getCurrentTime()) / getTic().getZip();
            ScheduledFuture sf = stpe.schedule(() -> manage(task.getRequest()),
                    convertedExecTime, TimeUnit.MILLISECONDS);
            task.setScheduledFuture(sf);
        });
    }

    @Override
    public final void onStopTime() {
//        Log.info("task scheduler onStop");
        clear();
        taskMap.clear();
    }

    /**
     * Fire a request and removed it from the map.
     *
     * @param sr request to manage
     */
    private void manage(final ScheduledRequest sr) {
        if (toExecute != null && toExecute.getConnectedBindingsSize() > 0) {
//            if (!sr.getSequence().equals("")) {
//                if (!sequenceMap.containsKey(sr.getSequence())) {
//                    sequenceMap.put(sr.getSequence(), 0l);
//                }
//                sr.setSequenceNumber(sequenceMap.get(sr.getSequence()));
//                sequenceMap.put(sr.getSequence(), sequenceMap.get(sr.getSequence()) + 1);
//            }
//            System.out.println("task scheduler send sr to " + sr.getDest() + " planned for " + new Date(sr.getExecTime()));
            toExecute.send(sr.toString(), null);
        }
        taskMap.remove(sr.getId());
    }

    /**
     * Cancel a specific scheduled request
     *
     * @param id Id of the scheduled request
     * @return
     */
    public final boolean cancel(UUID id) {
        if (taskMap.containsKey(id)) {
            taskMap.get(id).getScheduledFuture().cancel(true);
            taskMap.remove(id);
            return true;
        }
        return false;
    }

    public final void clear() {
        try {
            // Cancel scheduled but not started task, and avoid new ones
            stpe.shutdown();
            // Wait for the running tasks
            stpe.awaitTermination(500, TimeUnit.MILLISECONDS);
            // Interrupt the threads and shutdown the scheduler
            stpe.shutdownNow();
        } catch (InterruptedException exception) {
            exception.printStackTrace();
            Log.error("Interrupted Exception: " + exception.getMessage());
        }
    }

    public final void sendResponse(final Request req,
                                   final Object result) {
        if (pushResponse != null
                && pushResponse.getConnectedBindingsSize() > 0) {
            Response response = new Response(req.getId(),
                    getFullId(), req.getSrc(), getCurrentTime(), result);
            pushResponse.send(response.toString(), null);
        }
    }

    @Override
    protected RequestHandler getRequestHandler(Request request) {
        return this;
    }

    private  void initExecutor() {
        stpe = new ScheduledThreadPoolExecutor(1, r -> {
            return new Thread(r, getFullId() + "-taskscheduler-pool");
        });
    }

}



