package org.activehome.task;

/*
 * #%L
 * Active Home :: Task
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 Active Home Project
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


import org.activehome.com.ScheduledRequest;

import java.util.concurrent.ScheduledFuture;

/**
 * @author Jacky Bourgeois
 */
public class Task {

    private final ScheduledRequest scheduledRequest;
    private ScheduledFuture scheduledFuture;
    private long delayPause;

    public Task(final ScheduledRequest sr,
                final ScheduledFuture sf) {
        scheduledRequest = sr;
        scheduledFuture = sf;
        delayPause = 0;
    }

    public final ScheduledRequest getRequest() {
        return scheduledRequest;
    }

    public final ScheduledFuture getScheduledFuture() {
        return scheduledFuture;
    }

    public final void setScheduledFuture(final ScheduledFuture sf) {
        scheduledFuture = sf;
    }

    public final long getDelayPause() {
        return delayPause;
    }

    public final void setDelayPause(final long delay) {
        this.delayPause = delay;
    }
}
