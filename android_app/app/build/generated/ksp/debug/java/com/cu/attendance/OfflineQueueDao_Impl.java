package com.cu.attendance;

import android.database.Cursor;
import android.os.CancellationSignal;
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

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class OfflineQueueDao_Impl implements OfflineQueueDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<OfflineQueueEntity> __insertionAdapterOfOfflineQueueEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteById;

  private final SharedSQLiteStatement __preparedStmtOfClear;

  public OfflineQueueDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfOfflineQueueEntity = new EntityInsertionAdapter<OfflineQueueEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `offline_queue` (`id`,`action`,`uid`,`payload`,`createdAt`,`session`) VALUES (nullif(?, 0),?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final OfflineQueueEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getAction());
        statement.bindString(3, entity.getUid());
        if (entity.getPayload() == null) {
          statement.bindNull(4);
        } else {
          statement.bindString(4, entity.getPayload());
        }
        statement.bindLong(5, entity.getCreatedAt());
        statement.bindString(6, entity.getSession());
      }
    };
    this.__preparedStmtOfDeleteById = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM offline_queue WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClear = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM offline_queue WHERE session = ?";
        return _query;
      }
    };
  }

  @Override
  public Object enqueue(final OfflineQueueEntity item,
      final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfOfflineQueueEntity.insertAndReturnId(item);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteById(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteById.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
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
          __preparedStmtOfDeleteById.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clear(final String session, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClear.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, session);
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
          __preparedStmtOfClear.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object getAll(final String session,
      final Continuation<? super List<OfflineQueueEntity>> $completion) {
    final String _sql = "SELECT * FROM offline_queue WHERE session = ? ORDER BY createdAt ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, session);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<OfflineQueueEntity>>() {
      @Override
      @NonNull
      public List<OfflineQueueEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfAction = CursorUtil.getColumnIndexOrThrow(_cursor, "action");
          final int _cursorIndexOfUid = CursorUtil.getColumnIndexOrThrow(_cursor, "uid");
          final int _cursorIndexOfPayload = CursorUtil.getColumnIndexOrThrow(_cursor, "payload");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfSession = CursorUtil.getColumnIndexOrThrow(_cursor, "session");
          final List<OfflineQueueEntity> _result = new ArrayList<OfflineQueueEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final OfflineQueueEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpAction;
            _tmpAction = _cursor.getString(_cursorIndexOfAction);
            final String _tmpUid;
            _tmpUid = _cursor.getString(_cursorIndexOfUid);
            final String _tmpPayload;
            if (_cursor.isNull(_cursorIndexOfPayload)) {
              _tmpPayload = null;
            } else {
              _tmpPayload = _cursor.getString(_cursorIndexOfPayload);
            }
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final String _tmpSession;
            _tmpSession = _cursor.getString(_cursorIndexOfSession);
            _item = new OfflineQueueEntity(_tmpId,_tmpAction,_tmpUid,_tmpPayload,_tmpCreatedAt,_tmpSession);
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
