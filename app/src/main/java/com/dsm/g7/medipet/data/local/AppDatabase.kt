package com.dsm.g7.medipet.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.migration.Migration

class Converters {
    @TypeConverter
    fun fromAppointmentStatus(value: AppointmentStatus): String = value.name

    @TypeConverter
    fun toAppointmentStatus(value: String): AppointmentStatus =
        AppointmentStatus.valueOf(value)
}

@Database(
    entities = [Pet::class, Vaccine::class, Appointment::class, MedicalRecord::class, WeightRecord::class],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun petDao(): PetDao
    abstract fun vaccineDao(): VaccineDao
    abstract fun appointmentDao(): AppointmentDao
    abstract fun medicalRecordDao(): MedicalRecordDao
    abstract fun weightRecordDao(): WeightRecordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `vaccines` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`petId` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                    "`vetName` TEXT NOT NULL, `dateMillis` INTEGER NOT NULL, " +
                    "`isApplied` INTEGER NOT NULL DEFAULT 0)"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `appointments` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`petId` TEXT NOT NULL, `petName` TEXT NOT NULL, `ownerUid` TEXT NOT NULL, " +
                    "`dateMillis` INTEGER NOT NULL, `reason` TEXT NOT NULL, " +
                    "`vetName` TEXT NOT NULL, `status` TEXT NOT NULL, `notes` TEXT NOT NULL DEFAULT '')"
                )
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `medical_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`petId` TEXT NOT NULL, `ownerUid` TEXT NOT NULL, " +
                    "`dateMillis` INTEGER NOT NULL, `diagnosis` TEXT NOT NULL, " +
                    "`treatment` TEXT NOT NULL, `medications` TEXT NOT NULL, " +
                    "`vetName` TEXT NOT NULL, `photoUri` TEXT NOT NULL DEFAULT '')"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `weight_records` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`petId` TEXT NOT NULL, " +
                    "`weightKg` REAL NOT NULL, " +
                    "`dateMillis` INTEGER NOT NULL)"
                )
                database.execSQL(
                    "ALTER TABLE medical_records ADD COLUMN voiceNoteUrl TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE appointments ADD COLUMN photoUri TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE appointments ADD COLUMN voiceNoteUrl TEXT NOT NULL DEFAULT ''"
                )
                database.execSQL(
                    "ALTER TABLE appointments ADD COLUMN firestoreId TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "medipet_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
