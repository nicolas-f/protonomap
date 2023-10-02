/*
 * This file is part of the NoiseCapture application and OnoMap system.
 *
 * The 'OnoMaP' system is led by Lab-STICC and Ifsttar and generates noise maps via
 * citizen-contributed noise data.
 *
 * This application is co-funded by the ENERGIC-OD Project (European Network for
 * Redistributing Geospatial Information to user Communities - Open Data). ENERGIC-OD
 * (http://www.energic-od.eu/) is partially funded under the ICT Policy Support Programme (ICT
 * PSP) as part of the Competitiveness and Innovation Framework Programme by the European
 * Community. The application work is also supported by the French geographic portal GEOPAL of the
 * Pays de la Loire region (http://www.geopal.org).
 *
 * Copyright (C) IFSTTAR - LAE and Lab-STICC – CNRS UMR 6285 Equipe DECIDE Vannes
 *
 * NoiseCapture is a free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of
 * the License, or(at your option) any later version. NoiseCapture is distributed in the hope that
 * it will be useful,but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.You should have received a copy of the GNU General Public License along with this
 * program; if not, write to the Free Software Foundation,Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA 02110-1301  USA or see For more information,  write to Ifsttar,
 * 14-20 Boulevard Newton Cite Descartes, Champs sur Marne F-77447 Marne la Vallee Cedex 2 FRANCE
 *  or write to scientific.computing@ifsttar.fr
 */

package org.noise_planet.noisecapture;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.BaseColumns;
import androidx.annotation.ColorRes;
import androidx.annotation.IdRes;
import android.text.TextUtils;

import org.noise_planet.noisecapture.util.TrafficNoiseEstimator;

import java.text.DateFormat;
import java.util.Date;

/**
 * Handle database schema creation and upgrade
 */
public class Storage extends SQLiteOpenHelper {
    // Untranslated Tags, in the same order as displayed in the layouts
    public static final TagInfo[] TAGS_INFO = {t(0, "test", R.id.tags_measurement_conditions), t
            (3, "indoor", R.id.tags_measurement_conditions), t(1, "rain", R.id
            .tags_measurement_conditions), t(2, "wind", R.id.tags_measurement_conditions), t(5,
            "chatting", R.id.tags_predominant_sound_sources_col1, R.color.tag_group_human), t(12,
            "children", R.id.tags_predominant_sound_sources_col1, R.color.tag_group_human), t(4,
            "footsteps", R.id.tags_predominant_sound_sources_col1, R.color.tag_group_human), t
            (13, "music", R.id.tags_predominant_sound_sources_col1, R.color.tag_group_human), t
            (14, "road", R.id.tags_predominant_sound_sources_col2, R.color.tag_group_traffic), t
            (15, "rail", R.id.tags_predominant_sound_sources_col2, R.color.tag_group_traffic), t
            (10, "air_traffic", R.id.tags_predominant_sound_sources_col2, R.color
                    .tag_group_traffic), t(16, "marine_traffic", R.id
            .tags_predominant_sound_sources_col2, R.color.tag_group_traffic), t(19, "water", R.id
            .tags_predominant_sound_sources_col3, R.color.tag_group_natural), t(20, "animals", R
            .id.tags_predominant_sound_sources_col3, R.color.tag_group_natural), t(21,
            "vegetation", R.id.tags_predominant_sound_sources_col3, R.color.tag_group_natural), t(9, "works", R.id
            .tags_predominant_sound_sources_col4, R.color.tag_group_work), t(17, "alarms", R.id
            .tags_predominant_sound_sources_col4, R.color.tag_group_work), t(18, "industrial", R
            .id.tags_predominant_sound_sources_col4, R.color.tag_group_work)};

    /**
     * Convert audio device index into name
     * @see android.media.AudioDeviceInfo
     */
    public static final String[] microphoneDeviceTypeName = new String[]
            {"TYPE_UNKNOWN","TYPE_BUILTIN_EARPIECE","TYPE_BUILTIN_SPEAKER","TYPE_WIRED_HEADSET",
                    "TYPE_WIRED_HEADPHONES","TYPE_LINE_ANALOG","TYPE_LINE_DIGITAL","TYPE_BLUETOOTH_SCO",
                    "TYPE_BLUETOOTH_A2DP","TYPE_HDMI","TYPE_HDMI_ARC","TYPE_USB_DEVICE","TYPE_USB_ACCESSORY",
                    "TYPE_DOCK","TYPE_FM","TYPE_BUILTIN_MIC","TYPE_FM_TUNER","TYPE_TV_TUNER","TYPE_TELEPHONY",
                    "TYPE_AUX_LINE","TYPE_IP","TYPE_BUS","TYPE_USB_HEADSET","TYPE_HEARING_AID",
                    "TYPE_BUILTIN_SPEAKER_SAFE","TYPE_REMOTE_SUBMIX","TYPE_BLE_HEADSET","TYPE_BLE_SPEAKER",
                    "TYPE_ECHO_REFERENCE","TYPE_HDMI_EARC","TYPE_BLE_BROADCAST"};

    private static TagInfo t(int id, String name, @IdRes int location, @ColorRes int color) {
        return new TagInfo(id, name, location, color);
    }

    private static TagInfo t(int id, String name, @IdRes int location) {
        return new TagInfo(id, name, location, -1);
    }
    public static final class TagInfo {
        // Id to peek into string.xml used when translation must be done
        public final int id;
        // Name stored in database
        public final String name;
        public final
        @IdRes
        int location;
        public final
        @ColorRes
        int color;

        public TagInfo(int id, String name, int location, int color) {
            this.id = id;
            this.name = name;
            this.location = location;
            this.color = color;
        }
    }
    // If you change the database schema, you must increment the database version.
    public static final int DATABASE_VERSION = 13;
    public static final String DATABASE_NAME = "Storage.db";
    private static final String ACTIVATE_FOREIGN_KEY = "PRAGMA foreign_keys=ON;";

    public Storage(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(ACTIVATE_FOREIGN_KEY);
        db.execSQL(CREATE_RECORD);
        db.execSQL(CREATE_LEQ);
        db.execSQL(CREATE_LEQ_VALUE);
        db.execSQL(CREATE_RECORD_TAG);
        db.execSQL(CREATE_TRAFFIC_CALIBRATION_SESSION);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Upgrade queries
        // Do not use static table and column names
        if(oldVersion == 1) {
            // Upgrade from 1 to version 2
            // Add record length attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE record ADD COLUMN time_length INTEGER");
            }
            oldVersion = 2;
        }
        if(oldVersion == 2) {
            // Add gps speed and bearing attribute
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE leq ADD COLUMN speed FLOAT");
                db.execSQL("ALTER TABLE leq ADD COLUMN bearing FLOAT");
            }
            oldVersion = 3;
        }
        if(oldVersion == 3) {
            if(db.isReadOnly()) {
                // New feature, user input
                db.execSQL("ALTER TABLE leq ADD COLUMN description TEXT");
                db.execSQL("ALTER TABLE leq ADD COLUMN pleasantness SMALLINT DEFAULT 2");
                db.execSQL("ALTER TABLE leq ADD COLUMN photo_miniature BLOB");
                db.execSQL("ALTER TABLE leq ADD COLUMN photo_uri TEXT");
                db.execSQL( "CREATE TABLE record_tag(tag_id INTEGER PRIMARY KEY, record_id INTEGER, " +
                            "PRIMARY KEY(tag_id, record_id), " +
                            "FOREIGN KEY(record_id) REFERENCES  record(record_id) ON DELETE CASCADE);");
            }
            oldVersion = 4;
        }
        if(oldVersion == 4) {
            db.execSQL("ALTER TABLE record_tag ADD COLUMN tag_system_name TEXT");
            oldVersion = 5;
        }
        if(oldVersion == 5) {
            // Copy content to new table
            db.execSQL("ALTER TABLE record rename to record_old;");
            db.execSQL( "CREATE TABLE record(record_id INTEGER PRIMARY KEY, record_utc LONG," +
                    " upload_id TEXT, leq_mean FLOAT, time_length INTEGER, description TEXT," +
                    " photo_uri TEXT, pleasantness SMALLINT DEFAULT 2);");
            db.execSQL("INSERT INTO record SELECT record_id , record_utc ,upload_id , leq_mean ," +
                    " time_length , description ,photo_uri , pleasantness from record_old;");
            db.execSQL("DROP TABLE IF EXISTS record_old;");
            oldVersion = 6;
        }
        if(oldVersion == 6) {
            if (!db.isReadOnly()) {
                db.execSQL("ALTER TABLE record ADD COLUMN calibration_gain FLOAT DEFAULT 0");
            }
            oldVersion = 7;
        }
        if(oldVersion == 7) {
            if(!db.isReadOnly()) {
                // Up to version 7, there was a swapping of speed and bearing
                db.execSQL("UPDATE leq SET bearing=speed, speed=bearing;");
            }
            oldVersion = 8;
        }
        if(oldVersion == 8) {
            if(!db.isReadOnly()) {
                db.execSQL("ALTER TABLE record add column noiseparty_tag TEXT");
            }
            oldVersion = 9;
        }
        if(oldVersion == 9) {
            if(!db.isReadOnly()) {
                db.execSQL("CREATE TABLE traffic_calibration_session  ( session_id " +
                        "INTEGER PRIMARY KEY, median_peak  DOUBLE, traffic_count INTEGER," +
                        " estimated_distance DOUBLE, estimated_speed DOUBLE, calibration_utc LONG)");
            }
            oldVersion = 10;
        }
        if(oldVersion == 10) {
            if(!db.isReadOnly()) {
                db.execSQL("ALTER TABLE RECORD ADD COLUMN calibration_method INTEGER DEFAULT 0");
            }
            oldVersion = 11;
        }
        if(oldVersion == 11) {
            if(!db.isReadOnly()) {
                db.execSQL("ALTER TABLE RECORD ADD COLUMN microphone_device_id TEXT DEFAULT ''");
                db.execSQL("ALTER TABLE RECORD ADD COLUMN microphone_device_settings TEXT DEFAULT ''");
            }
            oldVersion = 12;
        }
        if(oldVersion == 12) {
            if(!db.isReadOnly()) {
                db.execSQL("ALTER TABLE LEQ ADD COLUMN laeq FLOAT DEFAULT 0");
            }
            oldVersion = 13;
        }
    }


    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            // Enable foreign key constraints
            db.execSQL(ACTIVATE_FOREIGN_KEY);
        }
    }

    private static Double getDouble(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getDouble(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Long getLong(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getLong(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Integer getInt(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return (int) cursor.getLong(colIndex);
            }
        } else {
            return null;
        }
    }

    private static Float getFloat(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getFloat(colIndex);
            }
        } else {
            return null;
        }
    }

    private static String getString(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                return cursor.getString(colIndex);
            }
        } else {
            return null;
        }
    }
    private static Bitmap getBitmap(Cursor cursor, String field) {
        int colIndex = cursor.getColumnIndex(field);
        if(colIndex != -1) {
            if(cursor.isNull(colIndex)) {
                return null;
            } else {
                byte[] byteData = cursor.getBlob(colIndex);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inScaled = false;
                return BitmapFactory.decodeByteArray(byteData, 0, byteData.length, options);
            }
        } else {
            return null;
        }
    }

    public static class Record implements BaseColumns {
        public enum CALIBRATION_METHODS {
            None, // Default state, no calibration
            System , // Use Android system information provided by Constructor (Sensibility @94dB 1000 Hz)
            ManualSetting, // The user overwrite the value manually in the settings
            Calibrator, // Calibration with calibrator by the user @94dB 1000 Hz
            Reference, // Calibration by the user with reference sound level meter
            CalibratedSmartPhone, // Auto calibration with reference smartphone having NoiseCapture
            Traffic // Calibration using road traffic as reference
        }

        public static final String TABLE_NAME = "record";
        public static final String COLUMN_ID = "record_id";
        public static final String COLUMN_UTC = "record_utc";
        public static final String COLUMN_UPLOAD_ID = "upload_id";
        public static final String COLUMN_LEQ_MEAN = "leq_mean";
        public static final String COLUMN_TIME_LENGTH = "time_length";
        public static final String COLUMN_DESCRIPTION = "description";
        public static final String COLUMN_PLEASANTNESS = "pleasantness";
        public static final String COLUMN_PHOTO_URI = "photo_uri";
        public static final String COLUMN_CALIBRATION_GAIN = "calibration_gain";
        public static final String COLUMN_NOISEPARTY_TAG = "noiseparty_tag";
        public static final String COLUMN_CALIBRATION_METHOD = "calibration_method";
        public static final String COLUMN_MICROPHONE_DEVICE_ID = "microphone_device_id";
        public static final String COLUMN_MICROPHONE_DEVICE_SETTINGS = "microphone_device_settings"; // dictionary characteristics of the microphone

        private int id;
        private long utc;
        private String uploadId;
        private float leqMean;
        private int timeLength;
        private String description;
        private Integer pleasantness;
        private Uri photoUri;
        private float calibrationGain;
        private String noisePartyTag;
        private CALIBRATION_METHODS calibrationMethod;
        private String microphoneDeviceId;
        private String microphoneDeviceSettings;


        @SuppressLint("Range")
        public Record(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_UTC)),
                    cursor.getString(cursor.getColumnIndex(COLUMN_UPLOAD_ID)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_LEQ_MEAN)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_TIME_LENGTH)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_CALIBRATION_GAIN)),
                    cursor.getColumnIndex(COLUMN_CALIBRATION_METHOD) != -1 ? cursor.getInt(cursor.getColumnIndex(COLUMN_CALIBRATION_METHOD)) : 0);
            noisePartyTag = getString(cursor, COLUMN_NOISEPARTY_TAG);
            description = getString(cursor, COLUMN_DESCRIPTION);
            String uriString = getString(cursor, COLUMN_PHOTO_URI);
            if(uriString != null && !uriString.isEmpty()) {
                photoUri = Uri.parse(uriString);
            }
            pleasantness = getInt(cursor, COLUMN_PLEASANTNESS);
            microphoneDeviceId = getString(cursor, COLUMN_MICROPHONE_DEVICE_ID);
            microphoneDeviceSettings = getString(cursor, COLUMN_MICROPHONE_DEVICE_SETTINGS);
        }

        public Record(int id, long utc, String uploadId, float leqMean, int timeLength,
                      float calibrationGain, int calibrationMethod) {
            this.id = id;
            this.utc = utc;
            this.uploadId = uploadId;
            this.leqMean = leqMean;
            this.timeLength = timeLength;
            this.calibrationGain = calibrationGain;
            this.calibrationMethod = CALIBRATION_METHODS.values()[calibrationMethod];
        }

        /**
         * @return The NoiseParty identifier for this measurement
         */
        public String getNoisePartyTag() {
            return noisePartyTag;
        }

        public void setNoisePartyTag(String noisePartyTag) {
            this.noisePartyTag = noisePartyTag;
        }

        public String getDescription() {
            return description;
        }

        public Integer getPleasantness() {
            return pleasantness;
        }

        public Uri getPhotoUri() {
            return photoUri;
        }

        public CALIBRATION_METHODS getCalibrationMethod() {
            return calibrationMethod;
        }

        public void setCalibrationMethod(CALIBRATION_METHODS calibrationMethod) {
            this.calibrationMethod = calibrationMethod;
        }

        /**
         * @return Calibration gain in dB
         */
        public float getCalibrationGain() {
            return calibrationGain;
        }

        /**
         * @return Record length in seconds
         */
        public int getTimeLength() {
            return timeLength;
        }

        /**
         * @return Upload identifier, empty if not uploaded
         */
        public String getUploadId() {
            return uploadId;
        }

        /**
         * @return Local storage identifier
         */
        public int getId() {
            return id;
        }

        /**
         * @return Record time
         */
        public long getUtc() {
            return utc;
        }

        public String getUtcDate() {
            return DateFormat.getDateTimeInstance().format(new Date(utc));
        }

        public float getLeqMean() {
            return leqMean;
        }

        /**
         * @return Microphone identifier
         */
        public String getMicrophoneDeviceId() {
            return microphoneDeviceId;
        }

        /**
         * @return Advanced microphone device settings (should be json)
         */
        public String getMicrophoneDeviceSettings() {
            return microphoneDeviceSettings;
        }
    }

    public static final String CREATE_RECORD = "CREATE TABLE " + Record.TABLE_NAME +
            "("+Record.COLUMN_ID +" INTEGER PRIMARY KEY, " +
            Record.COLUMN_UTC +" LONG, " +
            Record.COLUMN_UPLOAD_ID + " TEXT, " +
            Record.COLUMN_LEQ_MEAN + " FLOAT, " +
            Record.COLUMN_TIME_LENGTH + " INTEGER, " +
            Record.COLUMN_DESCRIPTION + " TEXT, " +
            Record.COLUMN_PHOTO_URI + " TEXT, " +
            Record.COLUMN_PLEASANTNESS + " SMALLINT," +
            Record.COLUMN_CALIBRATION_GAIN + " FLOAT DEFAULT 0," +
            Record.COLUMN_NOISEPARTY_TAG + " TEXT," +
            Record.COLUMN_CALIBRATION_METHOD + " INTEGER DEFAULT 0," +
            Record.COLUMN_MICROPHONE_DEVICE_ID + " TEXT," +
            Record.COLUMN_MICROPHONE_DEVICE_SETTINGS + " TEXT" +
            ")";


    public static class Leq implements BaseColumns {
        public static final String TABLE_NAME = "leq";
        public static final String COLUMN_RECORD_ID = "record_id";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_LEQ_UTC = "leq_utc";
        public static final String COLUMN_LATITUDE = "latitude";
        public static final String COLUMN_LONGITUDE = "longitude";
        public static final String COLUMN_ALTITUDE = "altitude";
        public static final String COLUMN_ACCURACY = "accuracy"; // location precision estimation
        public static final String COLUMN_SPEED = "speed"; // device speed estimation
        public static final String COLUMN_BEARING = "bearing"; // device orientation estimation
        public static final String COLUMN_LOCATION_UTC = "location_utc"; // date of last obtained location
        public static final String COLUMN_LAEQ = "laeq"; // dB(A) level

        private int recordId;
        private int leqId;
        private long leqUtc;
        private double latitude;
        private double longitude;
        private Double altitude;
        private Float speed;
        private Float bearing;
        private float accuracy;
        private long locationUTC;
        private float lAeq;

        /**
         * @param recordId Record id or -1 if unknown
         * @param leqId
         * @param leqUtc
         * @param latitude
         * @param longitude
         * @param altitude
         * @param speed
         * @param bearing
         * @param accuracy
         * @param locationUTC
         */
        public Leq(int recordId, int leqId, long leqUtc, double latitude, double longitude,
                   Double altitude, Float speed, Float bearing, float accuracy, long locationUTC,
                   float laeq) {
            this.recordId = recordId;
            this.leqId = leqId;
            this.leqUtc = leqUtc;
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.speed = speed;
            this.bearing = bearing;
            this.accuracy = accuracy;
            this.locationUTC = locationUTC;
            this.lAeq = (float)laeq;
        }

        @SuppressLint("Range")
        public Leq(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_RECORD_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LEQ_UTC)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LATITUDE)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_LONGITUDE)),
                    getDouble(cursor, COLUMN_ALTITUDE),
                    getFloat(cursor, COLUMN_SPEED),
                    getFloat(cursor, COLUMN_BEARING),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_ACCURACY)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_LOCATION_UTC)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_LAEQ)));
        }

        public static String getAllFields(String prepend) {
            return TextUtils.join(",", new String[]{prepend + COLUMN_RECORD_ID, prepend +
                    COLUMN_LEQ_ID, prepend + COLUMN_LEQ_UTC, prepend + COLUMN_LATITUDE, prepend +
                    COLUMN_LONGITUDE, prepend + COLUMN_ALTITUDE, prepend + COLUMN_ACCURACY,
                    prepend + COLUMN_SPEED, prepend + COLUMN_BEARING, prepend +
                    COLUMN_LOCATION_UTC, prepend + COLUMN_LAEQ});
        }

        public int getRecordId() {
            return recordId;
        }

        public int getLeqId() {
            return leqId;
        }

        public long getLeqUtc() {
            return leqUtc;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public Double getAltitude() {
            return altitude;
        }

        public float getAccuracy() {
            return accuracy;
        }

        /**
         * @return Device speed in ground m/s
         */
        public Float getSpeed() {
            return speed;
        }

        /**
         * @return Device orientation
         */
        public Float getBearing() {
            return bearing;
        }

        public long getLocationUTC() {
            return locationUTC;
        }

        public float getLAeq() {
            return lAeq;
        }
    }

    public static final String CREATE_LEQ = "CREATE TABLE " + Leq.TABLE_NAME + "(" +
            Leq.COLUMN_RECORD_ID + " INTEGER, " +
            Leq.COLUMN_LEQ_ID + " INTEGER PRIMARY KEY, " +
            Leq.COLUMN_LEQ_UTC + " LONG, " +
            Leq.COLUMN_LATITUDE + " DOUBLE, " +
            Leq.COLUMN_LONGITUDE + " DOUBLE, " +
            Leq.COLUMN_BEARING + " FLOAT, " +
            Leq.COLUMN_ALTITUDE + " DOUBLE, " +
            Leq.COLUMN_SPEED + " FLOAT, " +
            Leq.COLUMN_ACCURACY + " FLOAT, " +
            Leq.COLUMN_LOCATION_UTC + " LONG, " +
            Leq.COLUMN_LAEQ + " FLOAT, " +
            "FOREIGN KEY(" + Leq.COLUMN_RECORD_ID + ") REFERENCES record("+Record.COLUMN_ID+") ON DELETE CASCADE)";

    public static final class LeqValue implements BaseColumns {
        public static final String TABLE_NAME = "leq_value";
        public static final String COLUMN_LEQ_ID = "leq_id";
        public static final String COLUMN_FREQUENCY = "frequency";
        public static final String COLUMN_SPL = "spl"; // Spl value in dB(A)

        private final int leqId;
        private final int frequency;
        private final double spl;

        public LeqValue(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_LEQ_ID)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_FREQUENCY)),
                    cursor.getFloat(cursor.getColumnIndex(COLUMN_SPL)));
        }

        /**
         * @param leqId Leq Id or -1 if unknown
         * @param frequency Frequency in Hertz
         * @param spl Sound pressure value in dB(A)
         */
        public LeqValue(int leqId, int frequency, double spl) {
            this.leqId = leqId;
            this.frequency = frequency;
            this.spl = spl;
        }

        public int getLeqId() {
            return leqId;
        }

        public int getFrequency() {
            return frequency;
        }

        public double getSpl() {
            return spl;
        }
    }

    public static final String CREATE_LEQ_VALUE = "CREATE TABLE " + LeqValue.TABLE_NAME + "(" +
            LeqValue.COLUMN_LEQ_ID +" INTEGER, " +
            LeqValue.COLUMN_FREQUENCY +" INTEGER, " +
            LeqValue.COLUMN_SPL +" FLOAT, " +
            "PRIMARY KEY("+LeqValue.COLUMN_LEQ_ID +", "+LeqValue.COLUMN_FREQUENCY +"), " +
            "FOREIGN KEY("+LeqValue.COLUMN_LEQ_ID +") REFERENCES leq("+Leq.COLUMN_LEQ_ID+") ON DELETE CASCADE);";

    public static final class RecordTag {
        public static final String TABLE_NAME = "record_tag";
        public static final String COLUMN_TAG_ID = "tag_id";
        public static final String COLUMN_TAG_SYSTEM_NAME = "tag_system_name";
        public static final String COLUMN_RECORD_ID = "record_id";
    }

    public static final String CREATE_RECORD_TAG = "CREATE TABLE " + RecordTag.TABLE_NAME + "(" +
            RecordTag.COLUMN_TAG_ID + " INTEGER PRIMARY KEY, " +
            RecordTag.COLUMN_TAG_SYSTEM_NAME + " TEXT, " +
            RecordTag.COLUMN_RECORD_ID + " INTEGER, " +
            "FOREIGN KEY(" + RecordTag.COLUMN_RECORD_ID + ") REFERENCES " + Record.TABLE_NAME +
            "(" + Record.COLUMN_ID + ") ON DELETE CASCADE);";

    public static final class TrafficCalibrationSession  implements BaseColumns {
        public static final String TABLE_NAME = "traffic_calibration_session";
        public static final String COLUMN_CALIBRATION_ID = "session_id";
        public static final String COLUMN_MEDIAN_PEAK = "median_peak";
        public static final String COLUMN_TRAFFIC_COUNT = "traffic_count";
        public static final String COLUMN_ESTIMATED_SPEED = "estimated_speed";
        public static final String COLUMN_ESTIMATED_DISTANCE = "estimated_distance";
        public static final String COLUMN_MEASUREMENT_UTC = "calibration_utc";

        private final int sessionId;
        private final double medianPeak;
        private final int trafficCount;
        private double estimatedSpeed;
        private double estimatedDistance;
        private final long calibrationUTC;

        Double computedGain = null;

        public TrafficCalibrationSession(int sessionId, double medianPeak, int trafficCount, double estimatedSpeed, double estimatedDistance, long calibrationUTC) {
            this.sessionId = sessionId;
            this.medianPeak = medianPeak;
            this.trafficCount = trafficCount;
            this.estimatedSpeed = estimatedSpeed;
            this.estimatedDistance = estimatedDistance;
            this.calibrationUTC = calibrationUTC;
        }

        public ContentValues getContent() {
            ContentValues contentValues = new ContentValues();
            contentValues.put(Storage.TrafficCalibrationSession.COLUMN_MEDIAN_PEAK, getMedianPeak());
            contentValues.put(Storage.TrafficCalibrationSession.COLUMN_TRAFFIC_COUNT, getTrafficCount());
            contentValues.put(Storage.TrafficCalibrationSession.COLUMN_ESTIMATED_DISTANCE, getEstimatedDistance());
            contentValues.put(Storage.TrafficCalibrationSession.COLUMN_ESTIMATED_SPEED, getEstimatedSpeed());
            contentValues.put(Storage.TrafficCalibrationSession.COLUMN_MEASUREMENT_UTC, getUtc());
            return  contentValues;
        }

        public Double getComputedGain(TrafficNoiseEstimator trafficNoiseEstimator) {
            if(computedGain == null) {
                computedGain = trafficNoiseEstimator.computeGain(medianPeak);
            }
            return computedGain;
        }

        @SuppressLint("Range")
        public TrafficCalibrationSession(Cursor cursor) {
            this(cursor.getInt(cursor.getColumnIndex(COLUMN_CALIBRATION_ID)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_MEDIAN_PEAK)),
                    cursor.getInt(cursor.getColumnIndex(COLUMN_TRAFFIC_COUNT)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_ESTIMATED_SPEED)),
                    cursor.getDouble(cursor.getColumnIndex(COLUMN_ESTIMATED_DISTANCE)),
                    cursor.getLong(cursor.getColumnIndex(COLUMN_MEASUREMENT_UTC)));
        }

        public int getSessionId() {
            return sessionId;
        }

        public double getMedianPeak() {
            return medianPeak;
        }

        public int getTrafficCount() {
            return trafficCount;
        }

        public double getEstimatedSpeed() {
            return estimatedSpeed;
        }

        public double getEstimatedDistance() {
            return estimatedDistance;
        }
        /**
         * @return Record time
         */
        public long getUtc() {
            return calibrationUTC;
        }

        public void setEstimatedSpeed(double estimatedSpeed) {
            this.estimatedSpeed = estimatedSpeed;
        }

        public void setEstimatedDistance(double estimatedDistance) {
            this.estimatedDistance = estimatedDistance;
        }

        public String getUtcDate() {
            return DateFormat.getDateTimeInstance().format(new Date(calibrationUTC));
        }

    }

    public static final String CREATE_TRAFFIC_CALIBRATION_SESSION = "CREATE TABLE " + TrafficCalibrationSession.TABLE_NAME +
            "(" + TrafficCalibrationSession.COLUMN_CALIBRATION_ID +" INTEGER PRIMARY KEY, " +
            TrafficCalibrationSession.COLUMN_MEDIAN_PEAK+" DOUBLE, " +
            TrafficCalibrationSession.COLUMN_TRAFFIC_COUNT+" INTEGER, " +
            TrafficCalibrationSession.COLUMN_ESTIMATED_DISTANCE+" DOUBLE, " +
            TrafficCalibrationSession.COLUMN_ESTIMATED_SPEED+" DOUBLE," +
            TrafficCalibrationSession.COLUMN_MEASUREMENT_UTC+" LONG)";


}
