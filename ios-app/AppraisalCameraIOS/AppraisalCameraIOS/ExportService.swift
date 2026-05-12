import Foundation
import UIKit

enum ExportService {
    static func makePDF(store: AppState) throws -> URL {
        let photos = store.sortedPhotos
        guard !photos.isEmpty else { throw ExportError.empty }

        let url = store.temporaryExportURL(fileName: safeFileName(store: store, ext: "pdf"))
        let renderer = UIGraphicsPDFRenderer(bounds: pageRect)
        try renderer.writePDF(to: url) { context in
            for page in pageGroups(photos) {
                context.beginPage()
                drawPage(store: store, photos: page, in: pageRect)
            }
        }
        return url
    }

    static func makeJPGPages(store: AppState) throws -> [URL] {
        let photos = store.sortedPhotos
        guard !photos.isEmpty else { throw ExportError.empty }

        return try pageGroups(photos).enumerated().map { index, group in
            let image = UIGraphicsImageRenderer(size: pageRect.size).image { _ in
                drawPage(store: store, photos: group, in: pageRect)
            }
            guard let data = image.jpegData(compressionQuality: 0.9) else {
                throw ExportError.rendering
            }
            let url = store.temporaryExportURL(fileName: "\(safeBaseName(store: store))-page-\(index + 1).jpg")
            try data.write(to: url, options: .atomic)
            return url
        }
    }

    private static let pageRect = CGRect(x: 0, y: 0, width: 595, height: 842)

    private static func pageGroups(_ photos: [PhotoItem]) -> [[PhotoItem]] {
        stride(from: 0, to: photos.count, by: 2).map {
            Array(photos[$0..<min($0 + 2, photos.count)])
        }
    }

    private static func drawPage(store: AppState, photos: [PhotoItem], in rect: CGRect) {
        UIColor.white.setFill()
        UIRectFill(rect)

        let title = store.propertyAddress.isEmpty ? "사진자료" : store.propertyAddress
        drawText(title, in: CGRect(x: 36, y: 26, width: rect.width - 72, height: 28), font: .boldSystemFont(ofSize: 17), alignment: .center)

        let frames = [
            CGRect(x: 42, y: 82, width: rect.width - 84, height: 310),
            CGRect(x: 42, y: 452, width: rect.width - 84, height: 310)
        ]

        for (index, photo) in photos.enumerated() {
            let frame = frames[index]
            UIColor(white: 0.88, alpha: 1).setStroke()
            UIBezierPath(rect: frame).stroke()

            if let image = store.image(for: photo) {
                drawImageAspectFill(image, in: frame.insetBy(dx: 1, dy: 1))
            } else {
                drawText("사진 없음", in: frame, font: .systemFont(ofSize: 15), color: .darkGray, alignment: .center)
            }

            drawStamp(store.stampText(photo), in: frame)

            let captionRect = CGRect(x: frame.minX, y: frame.maxY + 8, width: frame.width, height: 42)
            drawText("\(store.photoTitle(photo))  \(store.photoCaption(photo))", in: captionRect, font: .systemFont(ofSize: 13), alignment: .left)
        }

        let pageText = "페이지"
        drawText(pageText, in: CGRect(x: 0, y: rect.height - 34, width: rect.width, height: 18), font: .systemFont(ofSize: 12), color: .darkGray, alignment: .center)
    }

    private static func drawImageAspectFill(_ image: UIImage, in rect: CGRect) {
        guard image.size.width > 0, image.size.height > 0 else { return }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let origin = CGPoint(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2)

        guard let context = UIGraphicsGetCurrentContext() else { return }
        context.saveGState()
        UIBezierPath(rect: rect).addClip()
        image.draw(in: CGRect(origin: origin, size: size))
        context.restoreGState()
    }

    private static func drawStamp(_ text: String, in photoRect: CGRect) {
        let attrs: [NSAttributedString.Key: Any] = [
            .font: UIFont.systemFont(ofSize: 11, weight: .semibold),
            .foregroundColor: UIColor.white
        ]
        let size = text.size(withAttributes: attrs)
        let box = CGRect(x: photoRect.maxX - size.width - 16, y: photoRect.maxY - size.height - 12, width: size.width + 10, height: size.height + 6)
        UIColor.black.withAlphaComponent(0.55).setFill()
        UIBezierPath(roundedRect: box, cornerRadius: 4).fill()
        text.draw(at: CGPoint(x: box.minX + 5, y: box.minY + 3), withAttributes: attrs)
    }

    private static func drawText(
        _ text: String,
        in rect: CGRect,
        font: UIFont,
        color: UIColor = .black,
        alignment: NSTextAlignment
    ) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = .byTruncatingTail
        let attrs: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: color,
            .paragraphStyle: paragraph
        ]
        text.draw(with: rect, options: [.usesLineFragmentOrigin, .truncatesLastVisibleLine], attributes: attrs)
    }

    private static func safeFileName(store: AppState, ext: String) -> String {
        "\(safeBaseName(store: store)).\(ext)"
    }

    private static func safeBaseName(store: AppState) -> String {
        let base = store.propertyAddress.isEmpty ? "사진자료" : store.propertyAddress
        let invalid = CharacterSet(charactersIn: "\\/:*?\"<>|")
        return base.components(separatedBy: invalid).joined(separator: "_")
    }

    enum ExportError: Error {
        case empty
        case rendering
    }
}
