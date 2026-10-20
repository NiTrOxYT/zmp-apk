package com.sitelink.manager.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
public final class TrustedHostKeyDao_Impl implements TrustedHostKeyDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TrustedHostKeyEntity> __insertionAdapterOfTrustedHostKeyEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteTrustedKey;

  public TrustedHostKeyDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTrustedHostKeyEntity = new EntityInsertionAdapter<TrustedHostKeyEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `trusted_host_keys` (`hostPortKey`,`host`,`port`,`keyType`,`fingerprint`,`rawKeyBase64`,`trustedAt`) VALUES (?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TrustedHostKeyEntity entity) {
        statement.bindString(1, entity.getHostPortKey());
        statement.bindString(2, entity.getHost());
        statement.bindLong(3, entity.getPort());
        statement.bindString(4, entity.getKeyType());
        statement.bindString(5, entity.getFingerprint());
        statement.bindString(6, entity.getRawKeyBase64());
        statement.bindLong(7, entity.getTrustedAt());
      }
    };
    this.__preparedStmtOfDeleteTrustedKey = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM trusted_host_keys WHERE hostPortKey = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOrUpdate(final TrustedHostKeyEntity key,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTrustedHostKeyEntity.insert(key);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteTrustedKey(final String hostPortKey,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteTrustedKey.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, hostPortKey);
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
          __preparedStmtOfDeleteTrustedKey.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getTrustedKey(final String hostPortKey,
      final Continuation<? super TrustedHostKeyEntity> $completion) {
    final String _sql = "SELECT * FROM trusted_host_keys WHERE hostPortKey = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, hostPortKey);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TrustedHostKeyEntity>() {
      @Override
      @Nullable
      public TrustedHostKeyEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHostPortKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hostPortKey");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfKeyType = CursorUtil.getColumnIndexOrThrow(_cursor, "keyType");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfRawKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "rawKeyBase64");
          final int _cursorIndexOfTrustedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "trustedAt");
          final TrustedHostKeyEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpHostPortKey;
            _tmpHostPortKey = _cursor.getString(_cursorIndexOfHostPortKey);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpKeyType;
            _tmpKeyType = _cursor.getString(_cursorIndexOfKeyType);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpRawKeyBase64;
            _tmpRawKeyBase64 = _cursor.getString(_cursorIndexOfRawKeyBase64);
            final long _tmpTrustedAt;
            _tmpTrustedAt = _cursor.getLong(_cursorIndexOfTrustedAt);
            _result = new TrustedHostKeyEntity(_tmpHostPortKey,_tmpHost,_tmpPort,_tmpKeyType,_tmpFingerprint,_tmpRawKeyBase64,_tmpTrustedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object findTrustedKey(final String key1, final String key2,
      final Continuation<? super TrustedHostKeyEntity> $completion) {
    final String _sql = "SELECT * FROM trusted_host_keys WHERE hostPortKey = ? OR hostPortKey = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, key1);
    _argIndex = 2;
    _statement.bindString(_argIndex, key2);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<TrustedHostKeyEntity>() {
      @Override
      @Nullable
      public TrustedHostKeyEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHostPortKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hostPortKey");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfKeyType = CursorUtil.getColumnIndexOrThrow(_cursor, "keyType");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfRawKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "rawKeyBase64");
          final int _cursorIndexOfTrustedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "trustedAt");
          final TrustedHostKeyEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpHostPortKey;
            _tmpHostPortKey = _cursor.getString(_cursorIndexOfHostPortKey);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpKeyType;
            _tmpKeyType = _cursor.getString(_cursorIndexOfKeyType);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpRawKeyBase64;
            _tmpRawKeyBase64 = _cursor.getString(_cursorIndexOfRawKeyBase64);
            final long _tmpTrustedAt;
            _tmpTrustedAt = _cursor.getLong(_cursorIndexOfTrustedAt);
            _result = new TrustedHostKeyEntity(_tmpHostPortKey,_tmpHost,_tmpPort,_tmpKeyType,_tmpFingerprint,_tmpRawKeyBase64,_tmpTrustedAt);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TrustedHostKeyEntity>> getAllTrustedKeysFlow() {
    final String _sql = "SELECT * FROM trusted_host_keys ORDER BY trustedAt DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"trusted_host_keys"}, new Callable<List<TrustedHostKeyEntity>>() {
      @Override
      @NonNull
      public List<TrustedHostKeyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHostPortKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hostPortKey");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfKeyType = CursorUtil.getColumnIndexOrThrow(_cursor, "keyType");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfRawKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "rawKeyBase64");
          final int _cursorIndexOfTrustedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "trustedAt");
          final List<TrustedHostKeyEntity> _result = new ArrayList<TrustedHostKeyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrustedHostKeyEntity _item;
            final String _tmpHostPortKey;
            _tmpHostPortKey = _cursor.getString(_cursorIndexOfHostPortKey);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpKeyType;
            _tmpKeyType = _cursor.getString(_cursorIndexOfKeyType);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpRawKeyBase64;
            _tmpRawKeyBase64 = _cursor.getString(_cursorIndexOfRawKeyBase64);
            final long _tmpTrustedAt;
            _tmpTrustedAt = _cursor.getLong(_cursorIndexOfTrustedAt);
            _item = new TrustedHostKeyEntity(_tmpHostPortKey,_tmpHost,_tmpPort,_tmpKeyType,_tmpFingerprint,_tmpRawKeyBase64,_tmpTrustedAt);
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

  @Override
  public Object getAllTrustedKeysList(
      final Continuation<? super List<TrustedHostKeyEntity>> $completion) {
    final String _sql = "SELECT * FROM trusted_host_keys";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TrustedHostKeyEntity>>() {
      @Override
      @NonNull
      public List<TrustedHostKeyEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHostPortKey = CursorUtil.getColumnIndexOrThrow(_cursor, "hostPortKey");
          final int _cursorIndexOfHost = CursorUtil.getColumnIndexOrThrow(_cursor, "host");
          final int _cursorIndexOfPort = CursorUtil.getColumnIndexOrThrow(_cursor, "port");
          final int _cursorIndexOfKeyType = CursorUtil.getColumnIndexOrThrow(_cursor, "keyType");
          final int _cursorIndexOfFingerprint = CursorUtil.getColumnIndexOrThrow(_cursor, "fingerprint");
          final int _cursorIndexOfRawKeyBase64 = CursorUtil.getColumnIndexOrThrow(_cursor, "rawKeyBase64");
          final int _cursorIndexOfTrustedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "trustedAt");
          final List<TrustedHostKeyEntity> _result = new ArrayList<TrustedHostKeyEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TrustedHostKeyEntity _item;
            final String _tmpHostPortKey;
            _tmpHostPortKey = _cursor.getString(_cursorIndexOfHostPortKey);
            final String _tmpHost;
            _tmpHost = _cursor.getString(_cursorIndexOfHost);
            final int _tmpPort;
            _tmpPort = _cursor.getInt(_cursorIndexOfPort);
            final String _tmpKeyType;
            _tmpKeyType = _cursor.getString(_cursorIndexOfKeyType);
            final String _tmpFingerprint;
            _tmpFingerprint = _cursor.getString(_cursorIndexOfFingerprint);
            final String _tmpRawKeyBase64;
            _tmpRawKeyBase64 = _cursor.getString(_cursorIndexOfRawKeyBase64);
            final long _tmpTrustedAt;
            _tmpTrustedAt = _cursor.getLong(_cursorIndexOfTrustedAt);
            _item = new TrustedHostKeyEntity(_tmpHostPortKey,_tmpHost,_tmpPort,_tmpKeyType,_tmpFingerprint,_tmpRawKeyBase64,_tmpTrustedAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
