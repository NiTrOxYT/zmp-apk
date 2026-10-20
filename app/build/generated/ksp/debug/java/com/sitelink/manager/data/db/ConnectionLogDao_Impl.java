package com.sitelink.manager.data.db;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class ConnectionLogDao_Impl implements ConnectionLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ConnectionLogEntity> __insertionAdapterOfConnectionLogEntity;

  private final SharedSQLiteStatement __preparedStmtOfClearLogs;

  public ConnectionLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfConnectionLogEntity = new EntityInsertionAdapter<ConnectionLogEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR ABORT INTO `connection_logs` (`id`,`siteId`,`siteName`,`connectionType`,`destinationIp`,`destinationPort`,`localPort`,`status`,`timestamp`,`details`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ConnectionLogEntity entity) {
        statement.bindLong(1, entity.getId());
        if (entity.getSiteId() == null) {
          statement.bindNull(2);
        } else {
          statement.bindLong(2, entity.getSiteId());
        }
        statement.bindString(3, entity.getSiteName());
        statement.bindString(4, entity.getConnectionType());
        statement.bindString(5, entity.getDestinationIp());
        statement.bindLong(6, entity.getDestinationPort());
        if (entity.getLocalPort() == null) {
          statement.bindNull(7);
        } else {
          statement.bindLong(7, entity.getLocalPort());
        }
        statement.bindString(8, entity.getStatus());
        statement.bindLong(9, entity.getTimestamp());
        if (entity.getDetails() == null) {
          statement.bindNull(10);
        } else {
          statement.bindString(10, entity.getDetails());
        }
      }
    };
    this.__preparedStmtOfClearLogs = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM connection_logs";
        return _query;
      }
    };
  }

  @Override
  public Object insertLog(final ConnectionLogEntity log,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfConnectionLogEntity.insertAndReturnId(log);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object clearLogs(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearLogs.acquire();
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearLogs.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ConnectionLogEntity>> getRecentLogsFlow() {
    final String _sql = "SELECT * FROM connection_logs ORDER BY timestamp DESC LIMIT 100";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"connection_logs"}, new Callable<List<ConnectionLogEntity>>() {
      @Override
      @NonNull
      public List<ConnectionLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfSiteId = CursorUtil.getColumnIndexOrThrow(_cursor, "siteId");
          final int _cursorIndexOfSiteName = CursorUtil.getColumnIndexOrThrow(_cursor, "siteName");
          final int _cursorIndexOfConnectionType = CursorUtil.getColumnIndexOrThrow(_cursor, "connectionType");
          final int _cursorIndexOfDestinationIp = CursorUtil.getColumnIndexOrThrow(_cursor, "destinationIp");
          final int _cursorIndexOfDestinationPort = CursorUtil.getColumnIndexOrThrow(_cursor, "destinationPort");
          final int _cursorIndexOfLocalPort = CursorUtil.getColumnIndexOrThrow(_cursor, "localPort");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfDetails = CursorUtil.getColumnIndexOrThrow(_cursor, "details");
          final List<ConnectionLogEntity> _result = new ArrayList<ConnectionLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ConnectionLogEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final Long _tmpSiteId;
            if (_cursor.isNull(_cursorIndexOfSiteId)) {
              _tmpSiteId = null;
            } else {
              _tmpSiteId = _cursor.getLong(_cursorIndexOfSiteId);
            }
            final String _tmpSiteName;
            _tmpSiteName = _cursor.getString(_cursorIndexOfSiteName);
            final String _tmpConnectionType;
            _tmpConnectionType = _cursor.getString(_cursorIndexOfConnectionType);
            final String _tmpDestinationIp;
            _tmpDestinationIp = _cursor.getString(_cursorIndexOfDestinationIp);
            final int _tmpDestinationPort;
            _tmpDestinationPort = _cursor.getInt(_cursorIndexOfDestinationPort);
            final Integer _tmpLocalPort;
            if (_cursor.isNull(_cursorIndexOfLocalPort)) {
              _tmpLocalPort = null;
            } else {
              _tmpLocalPort = _cursor.getInt(_cursorIndexOfLocalPort);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpDetails;
            if (_cursor.isNull(_cursorIndexOfDetails)) {
              _tmpDetails = null;
            } else {
              _tmpDetails = _cursor.getString(_cursorIndexOfDetails);
            }
            _item = new ConnectionLogEntity(_tmpId,_tmpSiteId,_tmpSiteName,_tmpConnectionType,_tmpDestinationIp,_tmpDestinationPort,_tmpLocalPort,_tmpStatus,_tmpTimestamp,_tmpDetails);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
