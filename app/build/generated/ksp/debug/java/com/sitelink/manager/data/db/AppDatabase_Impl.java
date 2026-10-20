package com.sitelink.manager.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AppDatabase_Impl extends AppDatabase {
  private volatile SiteDao _siteDao;

  private volatile TrustedHostKeyDao _trustedHostKeyDao;

  private volatile ConnectionLogDao _connectionLogDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(2) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sites` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `ipAddress` TEXT NOT NULL, `description` TEXT NOT NULL, `notes` TEXT NOT NULL, `portalPorts` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)");
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_sites_name` ON `sites` (`name`)");
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sites_ipAddress` ON `sites` (`ipAddress`)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `trusted_host_keys` (`hostPortKey` TEXT NOT NULL, `host` TEXT NOT NULL, `port` INTEGER NOT NULL, `keyType` TEXT NOT NULL, `fingerprint` TEXT NOT NULL, `rawKeyBase64` TEXT NOT NULL, `trustedAt` INTEGER NOT NULL, PRIMARY KEY(`hostPortKey`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `connection_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `siteId` INTEGER, `siteName` TEXT NOT NULL, `connectionType` TEXT NOT NULL, `destinationIp` TEXT NOT NULL, `destinationPort` INTEGER NOT NULL, `localPort` INTEGER, `status` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `details` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '167b2787a11b63515ac5b2c002c7c466')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sites`");
        db.execSQL("DROP TABLE IF EXISTS `trusted_host_keys`");
        db.execSQL("DROP TABLE IF EXISTS `connection_logs`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSites = new HashMap<String, TableInfo.Column>(8);
        _columnsSites.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("ipAddress", new TableInfo.Column("ipAddress", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("description", new TableInfo.Column("description", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("notes", new TableInfo.Column("notes", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("portalPorts", new TableInfo.Column("portalPorts", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSites.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSites = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSites = new HashSet<TableInfo.Index>(2);
        _indicesSites.add(new TableInfo.Index("index_sites_name", true, Arrays.asList("name"), Arrays.asList("ASC")));
        _indicesSites.add(new TableInfo.Index("index_sites_ipAddress", false, Arrays.asList("ipAddress"), Arrays.asList("ASC")));
        final TableInfo _infoSites = new TableInfo("sites", _columnsSites, _foreignKeysSites, _indicesSites);
        final TableInfo _existingSites = TableInfo.read(db, "sites");
        if (!_infoSites.equals(_existingSites)) {
          return new RoomOpenHelper.ValidationResult(false, "sites(com.sitelink.manager.data.db.SiteEntity).\n"
                  + " Expected:\n" + _infoSites + "\n"
                  + " Found:\n" + _existingSites);
        }
        final HashMap<String, TableInfo.Column> _columnsTrustedHostKeys = new HashMap<String, TableInfo.Column>(7);
        _columnsTrustedHostKeys.put("hostPortKey", new TableInfo.Column("hostPortKey", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("host", new TableInfo.Column("host", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("port", new TableInfo.Column("port", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("keyType", new TableInfo.Column("keyType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("fingerprint", new TableInfo.Column("fingerprint", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("rawKeyBase64", new TableInfo.Column("rawKeyBase64", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTrustedHostKeys.put("trustedAt", new TableInfo.Column("trustedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTrustedHostKeys = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTrustedHostKeys = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTrustedHostKeys = new TableInfo("trusted_host_keys", _columnsTrustedHostKeys, _foreignKeysTrustedHostKeys, _indicesTrustedHostKeys);
        final TableInfo _existingTrustedHostKeys = TableInfo.read(db, "trusted_host_keys");
        if (!_infoTrustedHostKeys.equals(_existingTrustedHostKeys)) {
          return new RoomOpenHelper.ValidationResult(false, "trusted_host_keys(com.sitelink.manager.data.db.TrustedHostKeyEntity).\n"
                  + " Expected:\n" + _infoTrustedHostKeys + "\n"
                  + " Found:\n" + _existingTrustedHostKeys);
        }
        final HashMap<String, TableInfo.Column> _columnsConnectionLogs = new HashMap<String, TableInfo.Column>(10);
        _columnsConnectionLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("siteId", new TableInfo.Column("siteId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("siteName", new TableInfo.Column("siteName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("connectionType", new TableInfo.Column("connectionType", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("destinationIp", new TableInfo.Column("destinationIp", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("destinationPort", new TableInfo.Column("destinationPort", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("localPort", new TableInfo.Column("localPort", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsConnectionLogs.put("details", new TableInfo.Column("details", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysConnectionLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesConnectionLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoConnectionLogs = new TableInfo("connection_logs", _columnsConnectionLogs, _foreignKeysConnectionLogs, _indicesConnectionLogs);
        final TableInfo _existingConnectionLogs = TableInfo.read(db, "connection_logs");
        if (!_infoConnectionLogs.equals(_existingConnectionLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "connection_logs(com.sitelink.manager.data.db.ConnectionLogEntity).\n"
                  + " Expected:\n" + _infoConnectionLogs + "\n"
                  + " Found:\n" + _existingConnectionLogs);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "167b2787a11b63515ac5b2c002c7c466", "b7f5e85bbb194afaffbc380bca62c264");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sites","trusted_host_keys","connection_logs");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `sites`");
      _db.execSQL("DELETE FROM `trusted_host_keys`");
      _db.execSQL("DELETE FROM `connection_logs`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SiteDao.class, SiteDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TrustedHostKeyDao.class, TrustedHostKeyDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ConnectionLogDao.class, ConnectionLogDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SiteDao siteDao() {
    if (_siteDao != null) {
      return _siteDao;
    } else {
      synchronized(this) {
        if(_siteDao == null) {
          _siteDao = new SiteDao_Impl(this);
        }
        return _siteDao;
      }
    }
  }

  @Override
  public TrustedHostKeyDao trustedHostKeyDao() {
    if (_trustedHostKeyDao != null) {
      return _trustedHostKeyDao;
    } else {
      synchronized(this) {
        if(_trustedHostKeyDao == null) {
          _trustedHostKeyDao = new TrustedHostKeyDao_Impl(this);
        }
        return _trustedHostKeyDao;
      }
    }
  }

  @Override
  public ConnectionLogDao connectionLogDao() {
    if (_connectionLogDao != null) {
      return _connectionLogDao;
    } else {
      synchronized(this) {
        if(_connectionLogDao == null) {
          _connectionLogDao = new ConnectionLogDao_Impl(this);
        }
        return _connectionLogDao;
      }
    }
  }
}
