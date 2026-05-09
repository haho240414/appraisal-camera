package com.codex.appraisalcamera;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class PptxExporter {
    private static final long SLIDE_CX = emu(595.5);
    private static final long SLIDE_CY = emu(842.25);
    private static final long PHOTO_X = emu(119.56);
    private static final long PHOTO_W = emu(357.17);
    private static final long PHOTO_H = emu(252.28);
    private static final long TOP_PHOTO_Y = emu(842.25 - 416.86 - 252.28);
    private static final long BOTTOM_PHOTO_Y = emu(842.25 - 117.05 - 252.28);
    private static final int NORMALIZED_IMAGE_WIDTH = 1429;
    private static final int NORMALIZED_IMAGE_HEIGHT = 1009;

    private PptxExporter() {
    }

    /**
     * Returns: 생성된 PPTX 바이트.
     * 도중에 한 사진이라도 실패하면 그 사진은 SKIP 하고 나머지로 PPTX 를 만든다.
     * 모두 실패하면 IOException.
     */
    static Result createWithStats(Context context, List<PhotoData> photos, String headerText) throws IOException {
        // 1) 사진을 미리 모두 디코드 — 실패한 것은 skip.
        ArrayList<DecodedPhoto> decoded = new ArrayList<>();
        int skipped = 0;
        for (PhotoData photo : photos) {
            try {
                byte[] jpegBytes = readJpeg(context, photo);
                decoded.add(new DecodedPhoto(photo, jpegBytes));
            } catch (Throwable t) {
                skipped++;
            }
        }
        if (decoded.isEmpty()) {
            throw new IOException("디코드할 수 있는 사진이 없습니다 (skipped=" + skipped + ")");
        }

        // 2) 디코드 성공한 사진만으로 슬라이드 구성.
        ArrayList<SlideData> slides = new ArrayList<>();
        for (int i = 0; i < decoded.size(); i += 2) {
            SlideData slide = new SlideData();
            slide.items.add(new SlideItem(decoded.get(i).photo, true));
            if (i + 1 < decoded.size()) {
                slide.items.add(new SlideItem(decoded.get(i + 1).photo, false));
            }
            slides.add(slide);
        }

        // 3) PPTX zip 작성.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            write(zip, "[Content_Types].xml", contentTypes(slides.size()));
            write(zip, "_rels/.rels", rootRels());
            write(zip, "docProps/app.xml", appXml(slides.size()));
            write(zip, "docProps/core.xml", coreXml());
            write(zip, "ppt/presentation.xml", presentationXml(slides.size()));
            write(zip, "ppt/_rels/presentation.xml.rels", presentationRels(slides.size()));
            write(zip, "ppt/slideMasters/slideMaster1.xml", slideMasterXml());
            write(zip, "ppt/slideMasters/_rels/slideMaster1.xml.rels", slideMasterRels());
            write(zip, "ppt/slideLayouts/slideLayout1.xml", slideLayoutXml());
            write(zip, "ppt/slideLayouts/_rels/slideLayout1.xml.rels", slideLayoutRels());
            write(zip, "ppt/theme/theme1.xml", themeXml());

            int imageIndex = 1;
            int decodedIndex = 0;
            for (int i = 0; i < slides.size(); i++) {
                int slideNumber = i + 1;
                SlideData slide = slides.get(i);
                for (SlideItem item : slide.items) {
                    item.relationshipId = "rId" + (slide.imageRels.size() + 2);
                    item.imageName = "image" + imageIndex + ".jpg";
                    slide.imageRels.add(item);
                    writeBytes(zip, "ppt/media/" + item.imageName, decoded.get(decodedIndex).jpegBytes);
                    imageIndex++;
                    decodedIndex++;
                }
                write(zip, "ppt/slides/slide" + slideNumber + ".xml", slideXml(slide, slideNumber, headerText));
                write(zip, "ppt/slides/_rels/slide" + slideNumber + ".xml.rels", slideRels(slide));
            }
        }
        return new Result(bytes.toByteArray(), skipped);
    }

    /** Backwards-compatible wrapper. */
    static byte[] create(Context context, List<PhotoData> photos, String headerText) throws IOException {
        return createWithStats(context, photos, headerText).bytes;
    }

    static final class Result {
        final byte[] bytes;
        final int skipped;
        Result(byte[] bytes, int skipped) { this.bytes = bytes; this.skipped = skipped; }
    }

    private static final class DecodedPhoto {
        final PhotoData photo;
        final byte[] jpegBytes;
        DecodedPhoto(PhotoData photo, byte[] jpegBytes) {
            this.photo = photo;
            this.jpegBytes = jpegBytes;
        }
    }

    private static byte[] readJpeg(Context context, PhotoData photo) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream input = context.getContentResolver().openInputStream(photo.uri)) {
            if (input == null) throw new IOException("Cannot open image");
            BitmapFactory.decodeStream(input, null, bounds);
        }

        int maxSide = Math.max(bounds.outWidth, bounds.outHeight);
        int sample = 1;
        while (maxSide / sample > 1800) {
            sample *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = sample;
        Bitmap bitmap;
        try (InputStream input = context.getContentResolver().openInputStream(photo.uri)) {
            if (input == null) throw new IOException("Cannot open image");
            bitmap = BitmapFactory.decodeStream(input, null, options);
        }
        if (bitmap == null) {
            throw new IOException("Cannot decode image");
        }
        Bitmap oriented = rotateBitmap(bitmap, readExifOrientation(context, photo.uri));
        Bitmap normalized = normalizeBitmap(oriented);
        photo.imageWidth = normalized.getWidth();
        photo.imageHeight = normalized.getHeight();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        normalized.compress(Bitmap.CompressFormat.JPEG, 88, out);
        normalized.recycle();
        if (oriented != bitmap) {
            oriented.recycle();
        }
        bitmap.recycle();
        return out.toByteArray();
    }

    private static int readExifOrientation(Context context, Uri uri) {
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) {
                return ExifInterface.ORIENTATION_NORMAL;
            }
            ExifInterface exif = new ExifInterface(input);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        } catch (IOException ignored) {
            return ExifInterface.ORIENTATION_NORMAL;
        }
    }

    private static Bitmap rotateBitmap(Bitmap source, int orientation) {
        int degrees;
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                degrees = 90;
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                degrees = 180;
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                degrees = 270;
                break;
            default:
                return source;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    private static Bitmap normalizeBitmap(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(NORMALIZED_IMAGE_WIDTH, NORMALIZED_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);
        canvas.drawColor(Color.rgb(245, 245, 245));

        float scale = Math.max(
                (float) NORMALIZED_IMAGE_WIDTH / source.getWidth(),
                (float) NORMALIZED_IMAGE_HEIGHT / source.getHeight()
        );
        float width = source.getWidth() * scale;
        float height = source.getHeight() * scale;
        float left = (NORMALIZED_IMAGE_WIDTH - width) / 2f;
        float top = (NORMALIZED_IMAGE_HEIGHT - height) / 2f;

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(source, null, new RectF(left, top, left + width, top + height), paint);
        return output;
    }

    private static String slideXml(SlideData slide, int pageNumber, String headerText) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        xml.append("<p:sld xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">");
        xml.append("<p:cSld><p:spTree>");
        xml.append("<p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr>");
        xml.append(textShape(2, headerText, emu(30.35), emu(20), emu(300), emu(24), 1100, false, "l"));
        xml.append(textShape(3, "사 진 용 지", emu(0), emu(80), SLIDE_CX, emu(36), 1800, true, "ctr"));
        xml.append(textShape(4, "Page : " + pageNumber, emu(505), emu(132), emu(80), emu(20), 1000, false, "r"));

        int id = 5;
        for (SlideItem item : slide.items) {
            long y = item.top ? TOP_PHOTO_Y : BOTTOM_PHOTO_Y;
            xml.append(frameRect(id++, PHOTO_X, y, PHOTO_W, PHOTO_H));
            xml.append(picture(id++, item.relationshipId, item.imageName, PHOTO_X, y, PHOTO_W, PHOTO_H));
            if (item.photo.stamp != null && !item.photo.stamp.isEmpty()) {
                xml.append(stampBox(id++, item.photo.stamp, PHOTO_X + PHOTO_W - emu(116), y + PHOTO_H - emu(24), emu(108), emu(17)));
            }
            xml.append(textShape(id++, item.photo.caption, emu(80), y + PHOTO_H + emu(22), emu(435), emu(25), 1100, false, "ctr"));
        }
        xml.append(textShape(id, "Page : " + pageNumber, emu(455), emu(805), emu(120), emu(20), 1000, false, "r"));
        xml.append("</p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sld>");
        return xml.toString();
    }

    private static String frameRect(int id, long x, long y, long w, long h) {
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"photo-frame\"/><p:cNvSpPr/><p:nvPr/></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + w + "\" cy=\"" + h + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val=\"F5F5F5\"/></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr></p:sp>";
    }

    private static String picture(int id, String relId, String name, long x, long y, long w, long h) {
        return "<p:pic><p:nvPicPr><p:cNvPr id=\"" + id + "\" name=\"" + escape(name) + "\"/><p:cNvPicPr><a:picLocks noChangeAspect=\"0\"/></p:cNvPicPr><p:nvPr/></p:nvPicPr>"
                + "<p:blipFill><a:blip r:embed=\"" + relId + "\"/><a:stretch><a:fillRect/></a:stretch></p:blipFill>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + w + "\" cy=\"" + h + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></p:spPr></p:pic>";
    }

    private static String stampBox(int id, String text, long x, long y, long w, long h) {
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"stamp\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + w + "\" cy=\"" + h + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:solidFill><a:srgbClr val=\"000000\"><a:alpha val=\"72000\"/></a:srgbClr></a:solidFill><a:ln><a:noFill/></a:ln></p:spPr>"
                + txBody(text, 850, false, "r", "FFFFFF")
                + "</p:sp>";
    }

    private static String textShape(int id, String text, long x, long y, long w, long h, int size, boolean bold, String align) {
        return "<p:sp><p:nvSpPr><p:cNvPr id=\"" + id + "\" name=\"text\"/><p:cNvSpPr txBox=\"1\"/><p:nvPr/></p:nvSpPr>"
                + "<p:spPr><a:xfrm><a:off x=\"" + x + "\" y=\"" + y + "\"/><a:ext cx=\"" + w + "\" cy=\"" + h + "\"/></a:xfrm><a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom><a:noFill/><a:ln><a:noFill/></a:ln></p:spPr>"
                + txBody(text, size, bold, align, "111111")
                + "</p:sp>";
    }

    private static String txBody(String text, int size, boolean bold, String align, String color) {
        StringBuilder body = new StringBuilder();
        body.append("<p:txBody><a:bodyPr wrap=\"square\" anchor=\"mid\"/><a:lstStyle/>");
        String[] lines = text.split("\\n", -1);
        for (String line : lines) {
            body.append("<a:p><a:pPr algn=\"").append(align).append("\"/>");
            body.append("<a:r><a:rPr lang=\"ko-KR\" sz=\"").append(size).append("\"");
            if (bold) body.append(" b=\"1\"");
            body.append("><a:solidFill><a:srgbClr val=\"").append(color).append("\"/></a:solidFill><a:latin typeface=\"Malgun Gothic\"/><a:ea typeface=\"Malgun Gothic\"/></a:rPr>");
            body.append("<a:t>").append(escape(line)).append("</a:t></a:r></a:p>");
        }
        body.append("</p:txBody>");
        return body.toString();
    }

    private static String contentTypes(int slideCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">");
        xml.append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>");
        xml.append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        xml.append("<Default Extension=\"jpg\" ContentType=\"image/jpeg\"/>");
        xml.append("<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>");
        xml.append("<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>");
        xml.append("<Override PartName=\"/ppt/presentation.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.presentation.main+xml\"/>");
        xml.append("<Override PartName=\"/ppt/slideMasters/slideMaster1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideMaster+xml\"/>");
        xml.append("<Override PartName=\"/ppt/slideLayouts/slideLayout1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slideLayout+xml\"/>");
        xml.append("<Override PartName=\"/ppt/theme/theme1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.theme+xml\"/>");
        for (int i = 1; i <= slideCount; i++) {
            xml.append("<Override PartName=\"/ppt/slides/slide").append(i).append(".xml\" ContentType=\"application/vnd.openxmlformats-officedocument.presentationml.slide+xml\"/>");
        }
        xml.append("</Types>");
        return xml.toString();
    }

    private static String rootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"ppt/presentation.xml\"/>"
                + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
                + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
                + "</Relationships>";
    }

    private static String presentationXml(int slideCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:presentation xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\">");
        xml.append("<p:sldMasterIdLst><p:sldMasterId id=\"2147483648\" r:id=\"rId1\"/></p:sldMasterIdLst><p:sldIdLst>");
        for (int i = 1; i <= slideCount; i++) {
            xml.append("<p:sldId id=\"").append(255 + i).append("\" r:id=\"rId").append(i + 1).append("\"/>");
        }
        xml.append("</p:sldIdLst><p:sldSz cx=\"").append(SLIDE_CX).append("\" cy=\"").append(SLIDE_CY).append("\" type=\"custom\"/><p:notesSz cx=\"6858000\" cy=\"9144000\"/></p:presentation>");
        return xml.toString();
    }

    private static String presentationRels(int slideCount) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        xml.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"slideMasters/slideMaster1.xml\"/>");
        for (int i = 1; i <= slideCount; i++) {
            xml.append("<Relationship Id=\"rId").append(i + 1).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide\" Target=\"slides/slide").append(i).append(".xml\"/>");
        }
        xml.append("</Relationships>");
        return xml.toString();
    }

    private static String slideRels(SlideData slide) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        xml.append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/>");
        for (SlideItem item : slide.imageRels) {
            xml.append("<Relationship Id=\"").append(item.relationshipId).append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"../media/").append(item.imageName).append("\"/>");
        }
        xml.append("</Relationships>");
        return xml.toString();
    }

    private static String slideMasterXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:sldMaster xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\"><p:cSld><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMap bg1=\"lt1\" tx1=\"dk1\" bg2=\"lt2\" tx2=\"dk2\" accent1=\"accent1\" accent2=\"accent2\" accent3=\"accent3\" accent4=\"accent4\" accent5=\"accent5\" accent6=\"accent6\" hlink=\"hlink\" folHlink=\"folHlink\"/><p:sldLayoutIdLst><p:sldLayoutId id=\"2147483649\" r:id=\"rId1\"/></p:sldLayoutIdLst><p:txStyles><p:titleStyle/><p:bodyStyle/><p:otherStyle/></p:txStyles></p:sldMaster>";
    }

    private static String slideMasterRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideLayout\" Target=\"../slideLayouts/slideLayout1.xml\"/><Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/theme\" Target=\"../theme/theme1.xml\"/></Relationships>";
    }

    private static String slideLayoutXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><p:sldLayout xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" xmlns:p=\"http://schemas.openxmlformats.org/presentationml/2006/main\" type=\"blank\" preserve=\"1\"><p:cSld name=\"Blank\"><p:spTree><p:nvGrpSpPr><p:cNvPr id=\"1\" name=\"\"/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr><p:grpSpPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"0\" cy=\"0\"/><a:chOff x=\"0\" y=\"0\"/><a:chExt cx=\"0\" cy=\"0\"/></a:xfrm></p:grpSpPr></p:spTree></p:cSld><p:clrMapOvr><a:masterClrMapping/></p:clrMapOvr></p:sldLayout>";
    }

    private static String slideLayoutRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/slideMaster\" Target=\"../slideMasters/slideMaster1.xml\"/></Relationships>";
    }

    private static String themeXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><a:theme xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" name=\"Appraisal\"><a:themeElements><a:clrScheme name=\"Office\"><a:dk1><a:srgbClr val=\"111111\"/></a:dk1><a:lt1><a:srgbClr val=\"FFFFFF\"/></a:lt1><a:dk2><a:srgbClr val=\"444444\"/></a:dk2><a:lt2><a:srgbClr val=\"F4F4F4\"/></a:lt2><a:accent1><a:srgbClr val=\"166C7D\"/></a:accent1><a:accent2><a:srgbClr val=\"666666\"/></a:accent2><a:accent3><a:srgbClr val=\"999999\"/></a:accent3><a:accent4><a:srgbClr val=\"BBBBBB\"/></a:accent4><a:accent5><a:srgbClr val=\"DDDDDD\"/></a:accent5><a:accent6><a:srgbClr val=\"EEEEEE\"/></a:accent6><a:hlink><a:srgbClr val=\"166C7D\"/></a:hlink><a:folHlink><a:srgbClr val=\"166C7D\"/></a:folHlink></a:clrScheme><a:fontScheme name=\"Malgun\"><a:majorFont><a:latin typeface=\"Malgun Gothic\"/><a:ea typeface=\"Malgun Gothic\"/></a:majorFont><a:minorFont><a:latin typeface=\"Malgun Gothic\"/><a:ea typeface=\"Malgun Gothic\"/></a:minorFont></a:fontScheme><a:fmtScheme name=\"Default\"><a:fillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:fillStyleLst><a:lnStyleLst><a:ln w=\"9525\"><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:ln></a:lnStyleLst><a:effectStyleLst><a:effectStyle><a:effectLst/></a:effectStyle></a:effectStyleLst><a:bgFillStyleLst><a:solidFill><a:schemeClr val=\"phClr\"/></a:solidFill></a:bgFillStyleLst></a:fmtScheme></a:themeElements><a:objectDefaults/><a:extraClrSchemeLst/></a:theme>";
    }

    private static String appXml(int slideCount) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\"><Application>AppraisalCamera</Application><PresentationFormat>A4 Portrait</PresentationFormat><Slides>" + slideCount + "</Slides></Properties>";
    }

    private static String coreXml() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"><dc:title>자체감정 사진자료</dc:title><dc:creator>AppraisalCamera</dc:creator></cp:coreProperties>";
    }

    private static void write(ZipOutputStream zip, String name, String text) throws IOException {
        writeBytes(zip, name, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    private static long emu(double points) {
        return Math.round(points * 12700);
    }

    private static String escape(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    static final class PhotoData {
        final Uri uri;
        final String caption;
        final String stamp;
        int imageWidth;
        int imageHeight;

        PhotoData(Uri uri, String caption, String stamp) {
            this.uri = uri;
            this.caption = caption;
            this.stamp = stamp;
        }
    }

    private static final class SlideData {
        final ArrayList<SlideItem> items = new ArrayList<>();
        final ArrayList<SlideItem> imageRels = new ArrayList<>();
    }

    private static final class SlideItem {
        final PhotoData photo;
        final boolean top;
        String relationshipId;
        String imageName;

        SlideItem(PhotoData photo, boolean top) {
            this.photo = photo;
            this.top = top;
        }
    }

}
