/*
 * Copyright 2020 Arman Sargsyan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zerobranch.androidremotedebugger.source.managers;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.os.Handler;
import android.os.HandlerThread;

import zerobranch.androidremotedebugger.AndroidRemoteDebugger;
import zerobranch.androidremotedebugger.source.local.StatusCodeFilter;
import zerobranch.androidremotedebugger.source.models.LogModel;
import zerobranch.androidremotedebugger.source.models.httplog.HttpLogModel;
import zerobranch.androidremotedebugger.source.repository.HttpLogRepository;
import zerobranch.androidremotedebugger.source.repository.LogRepository;

import java.util.List;

public final class ContinuousDBManager {
    private static final String DATABASE_NAME = "remote_debugger_data.db";
    private static final Object LOCK = new Object();
    private static ContinuousDBManager instance;
    private final Handler loggingHandler;
    private final HandlerThread loggingHandlerThread;
    private SQLiteDatabase database;
    private HttpLogRepository httpLogRepository;
    private LogRepository logRepository;

    private ContinuousDBManager(final Context context) {
        loggingHandlerThread = new HandlerThread("LoggingHandlerThread");
        loggingHandlerThread.start();
        loggingHandler = new Handler(loggingHandlerThread.getLooper());

        loggingHandler.post(() -> {
            SQLiteDatabase.deleteDatabase(context.getDatabasePath(DATABASE_NAME));
            database = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
            database.setVersion(Integer.MAX_VALUE);

            httpLogRepository = new HttpLogRepository(database);
            httpLogRepository.createHttpLogsTable(database);

            logRepository = new LogRepository(database);
            logRepository.createLogsTable(database);
        });
    }

    public static ContinuousDBManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AndroidRemoteDebugger is not initialized. " +
                    "Please call " + AndroidRemoteDebugger.class.getName() + ".init()");
        }
        return instance;
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            if (instance == null) {
                instance = new ContinuousDBManager(context);
            }
        }
    }

    public static void destroy() {
        if (instance != null) {
            instance.loggingHandlerThread.quit();

            if (instance.database != null && instance.database.isOpen()) {
                instance.database.close();
            }

            instance = null;
        }
    }

    public long addHttpLog(HttpLogModel logModel) {
        synchronized (LOCK) {
            return httpLogRepository.add(logModel);
        }
    }

    public void clearAllHttpLogs() {
        synchronized (LOCK) {
            loggingHandler.post(() -> httpLogRepository.clearAll());
        }
    }

    public void addLog(final LogModel model) {
        synchronized (LOCK) {
            loggingHandler.post(() -> logRepository.addLog(model));
        }
    }

    public List<LogModel> getLogsByFilter(int offset, int limit, String level, String tag, String search) {
        synchronized (LOCK) {
            return logRepository.getLogsByFilter(offset, limit, level, tag, search);
        }
    }

    public void clearAllLogs() {
        synchronized (LOCK) {
            loggingHandler.post(() -> logRepository.clearAllLogs());
        }
    }

    public List<HttpLogModel> getHttpLogs(int offset,
                                          int limit,
                                          StatusCodeFilter statusCode,
                                          boolean isOnlyErrors,
                                          String search) {
        synchronized (LOCK) {
            return httpLogRepository.getHttpLogs(offset, limit, statusCode, isOnlyErrors, search);
        }
    }
}
