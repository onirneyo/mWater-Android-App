package co.mwater.clientapp.dbsync;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

/**
 * SyncTable is a database table which is designed to have changes synchronized
 * with a central server. It uses a series of triggers to record when changes 
 * are made to its rows. It also stores a row version with each row so that
 * the server is able to correctly merge changes made against older copies
 * of rows.
 * 
 * Row version is always zero for newly created local rows. As soon as the 
 * server receives the row, it begins versioning it at version 1. The client
 * does not receive a copy back of the row from the server, but the version 
 * numbers remain different until the first update. 
 * 
 * That is, the row versions on the client refer to the row version against
 * which the changes are being made.
 * 
 * Deletes should always cascade, as server might not send deletes of child
 * rows.
 * @author Clayton
 *
 */
public abstract class SyncTable {
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_UID = "uid";
	public static final String COLUMN_ROW_VERSION = "row_version";
	public static final String COLUMN_CREATED_BY = "created_by";		// Optional field with enforced behavior when synced. Must be included in syncColumns

	private String[] syncColumns; // Columns, excluding any declared above except created_by
	private ForeignKey[] foreignKeys;
	
	public abstract String getTableName();
	public abstract String getCreateSql();
	
	public SyncTable(String[] syncColumns, ForeignKey[] foreignKeys) {
		this.syncColumns = syncColumns;
		this.foreignKeys = foreignKeys;
	}
	
	public String[] getSyncColumns() {
		return syncColumns;
	}
	
	public void onCreate(SQLiteDatabase database) {
		database.execSQL(getCreateSql());
		
		// Create trigger to synchronize
		database.execSQL(getInsertTriggerSql());
		database.execSQL(getUpdateTriggerSql());
		database.execSQL(getDeleteTriggerSql());
		
		// Create cascade delete trigger
		for (ForeignKey fk : foreignKeys)
			database.execSQL(getForeignKeyTriggerSql(fk));
	}

	String getInsertTriggerSql() {
		// Create a trigger which is fired when new rows are inserted with a row version of 0
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TRIGGER IF NOT EXISTS ");
		sql.append("inserttrigger").append(getTableName());
		sql.append(" AFTER INSERT ON ").append(getTableName());
		sql.append(" WHEN new.").append(COLUMN_ROW_VERSION).append("=0");
		sql.append(" BEGIN INSERT INTO ").append(SyncChangesTable.TABLE_NAME);
		sql.append(" (").append(SyncChangesTable.COLUMN_TABLENAME);
		sql.append(", ").append(SyncChangesTable.COLUMN_ROWUID);
		sql.append(", ").append(SyncChangesTable.COLUMN_ACTION).append(")");
		sql.append(" VALUES ('").append(getTableName()).append("',");
		sql.append(" new.").append(COLUMN_UID).append(",");
		sql.append(" 'I'); END");
		
		return sql.toString();
	}

	String getUpdateTriggerSql() {
		// Create a trigger which is fired when rows are updated without changing the row version
		// and without setting row version to -1
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TRIGGER IF NOT EXISTS ");
		sql.append("updatetrigger").append(getTableName());
		sql.append(" AFTER UPDATE ON ").append(getTableName());
		sql.append(" WHEN new.").append(COLUMN_ROW_VERSION).append("=old.").append(COLUMN_ROW_VERSION);
		sql.append(" AND new.").append(COLUMN_ROW_VERSION).append("<>-1");
		sql.append(" BEGIN INSERT INTO ").append(SyncChangesTable.TABLE_NAME);
		sql.append(" (").append(SyncChangesTable.COLUMN_TABLENAME);
		sql.append(", ").append(SyncChangesTable.COLUMN_ROWUID);
		sql.append(", ").append(SyncChangesTable.COLUMN_ACTION).append(")");
		sql.append(" VALUES ('").append(getTableName()).append("',");
		sql.append(" new.").append(COLUMN_UID).append(",");
		sql.append(" 'U'); END");
		
		return sql.toString();
	}
	
	String getDeleteTriggerSql() {
		// Create a trigger which is fired when rows with a non-negative row version are deleted
		// To avoid firing, first update row version to -1
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TRIGGER IF NOT EXISTS ");
		sql.append("deletetrigger").append(getTableName());
		sql.append(" AFTER DELETE ON ").append(getTableName());
		sql.append(" WHEN old.").append(COLUMN_ROW_VERSION).append(">=0");
		sql.append(" BEGIN INSERT INTO ").append(SyncChangesTable.TABLE_NAME);
		sql.append(" (").append(SyncChangesTable.COLUMN_TABLENAME);
		sql.append(", ").append(SyncChangesTable.COLUMN_ROWUID);
		sql.append(", ").append(SyncChangesTable.COLUMN_ACTION).append(")");
		sql.append(" VALUES ('").append(getTableName()).append("',");
		sql.append(" old.").append(COLUMN_UID).append(",");
		sql.append(" 'D'); END");
		
		return sql.toString();
	}
	
	String getForeignKeyTriggerSql(ForeignKey fk) {
		// Create a trigger which silently deletes child rows
		StringBuilder sql = new StringBuilder();
		sql.append("CREATE TRIGGER IF NOT EXISTS ");
		sql.append("fktrigger").append(getTableName()).append(fk.column);
		sql.append(" AFTER DELETE ON ").append(fk.destTable);
		sql.append(" BEGIN UPDATE ").append(getTableName());
		sql.append(" SET ").append(SyncTable.COLUMN_ROW_VERSION).append("=-1");
		sql.append(" WHERE ").append(fk.column).append("=old.").append(fk.destColumn);
		sql.append("; DELETE FROM ").append(getTableName());
		sql.append(" WHERE ").append(fk.column).append("=old.").append(fk.destColumn);
		sql.append("; END");
		
		return sql.toString();
}

	public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
		Log.w(getTableName(), "Upgrading database from version "
				+ oldVersion + " to " + newVersion
				+ ", which will destroy all old data");
		database.execSQL("DROP TABLE IF EXISTS " + getTableName());
		database.execSQL("DROP TRIGGER IF EXISTS inserttrigger" + getTableName());
		database.execSQL("DROP TRIGGER IF EXISTS updatetrigger" + getTableName());
		database.execSQL("DROP TRIGGER IF EXISTS deletetrigger" + getTableName());
		onCreate(database);
	}

	/**
	 * Foreign keys. Must be strings
	 * @author Clayton
	 *
	 */
	public static class ForeignKey {
		/**
		 * Creates a foreign key
		 * @param column column in child table
		 * @param destTable parent table
		 * @param destColumn parent column
		 */
		public ForeignKey(String column, String destTable, String destColumn) {
			this.column = column;
			this.destTable = destTable;
			this.destColumn = destColumn;
		}
		public final String column;
		public final String destTable;
		public final String destColumn;
	}

	public ForeignKey[] getForeignKeys() {
		return foreignKeys;
	}
}
