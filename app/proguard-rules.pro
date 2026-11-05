# Exif tags are enumerated reflectively when metadata preservation is requested.
-keepclassmembers class androidx.exifinterface.media.ExifInterface {
    public static java.lang.String TAG_*;
}
