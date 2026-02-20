# Keep app entry points referenced from manifest.
-keep class com.example.moneymind.MainActivity { *; }
-keep class com.example.moneymind.notification.BankNotificationListener { *; }
-keep class com.example.moneymind.MoneyMindApp { *; }

# Keep reflection-heavy parser libraries to avoid runtime regressions.
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.apache.commons.csv.** { *; }
-keep class com.tom_roush.** { *; }
-keep class org.apache.logging.log4j.** { *; }

# Keep service provider metadata-dependent types.
-keep class org.apache.poi.extractor.** { *; }
-keep class org.apache.poi.sl.draw.** { *; }
-keep class org.apache.poi.sl.usermodel.** { *; }
-keep class org.apache.poi.ss.usermodel.** { *; }

# Preserve metadata used by Kotlin/Room and reflection.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# POI/PDFBox pull optional desktop/xml stacks not used on Android runtime.
-dontwarn aQute.bnd.annotation.spi.**
-dontwarn com.gemalto.jp2.**
-dontwarn com.github.javaparser.**
-dontwarn com.sun.org.apache.xml.internal.resolver.**
-dontwarn de.rototor.pdfbox.graphics2d.**
-dontwarn edu.umd.cs.findbugs.annotations.**
-dontwarn java.awt.**
-dontwarn java.awt.color.**
-dontwarn java.awt.font.**
-dontwarn java.awt.geom.**
-dontwarn java.awt.image.**
-dontwarn javax.imageio.**
-dontwarn javax.swing.**
-dontwarn javax.xml.crypto.**
-dontwarn javax.xml.stream.**
-dontwarn net.sf.saxon.**
-dontwarn org.apache.batik.**
-dontwarn org.apache.jcp.xml.dsig.internal.dom.**
-dontwarn org.apache.maven.**
-dontwarn org.apache.pdfbox.**
-dontwarn org.apache.tools.ant.**
-dontwarn org.apache.xml.security.**
-dontwarn org.ietf.jgss.**
-dontwarn org.osgi.**
-dontwarn org.w3c.dom.events.**
-dontwarn org.w3c.dom.svg.**
-dontwarn org.w3c.dom.traversal.**
