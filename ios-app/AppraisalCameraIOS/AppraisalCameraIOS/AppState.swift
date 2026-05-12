import Foundation
import Observation
import SwiftUI
import UIKit

enum CaptureMode: String, Codable, CaseIterable, Identifiable {
    case appraisal
    case fieldSurvey

    var id: String { rawValue }

    var title: String {
        switch self {
        case .appraisal: return "자체감정"
        case .fieldSurvey: return "현지답사"
        }
    }
}

enum PhotoCategory: String, Codable, CaseIterable, Identifiable {
    case land
    case building
    case extra
    case custom
    case field

    var id: String { rawValue }

    var title: String {
        switch self {
        case .land: return "토지"
        case .building: return "건물"
        case .extra: return "제시외"
        case .custom: return "기타"
        case .field: return "현지답사"
        }
    }
}

struct PhotoItem: Identifiable, Codable, Hashable {
    var id: UUID
    var category: PhotoCategory
    var symbol: String
    var memo: String
    var fileName: String
    var createdAt: Date
    var debtorName: String
    var fieldSurveyor: String
}

@Observable
final class AppState {
    var mode: CaptureMode = .appraisal
    var propertyAddress: String = ""
    var currentCategory: PhotoCategory = .land
    var currentSymbol: String = "1"
    var customSymbol: String = ""
    var memo: String = ""
    var debtorName: String = ""
    var fieldSurveyor: String = ""
    var photos: [PhotoItem] = []
    var lastError: String?

    private let photoDirectoryName = "StoredPhotos"
    private let stateFileName = "app-state.json"

    init() {
        load()
        updateDefaultSymbol()
    }

    var sortedPhotos: [PhotoItem] {
        photos.sorted { left, right in
            let leftCategory = categoryRank(left.category)
            let rightCategory = categoryRank(right.category)
            if leftCategory != rightCategory { return leftCategory < rightCategory }

            let leftSymbol = symbolRank(left)
            let rightSymbol = symbolRank(right)
            if leftSymbol != rightSymbol { return leftSymbol < rightSymbol }

            return left.createdAt < right.createdAt
        }
    }

    func addPhoto(_ image: UIImage) {
        guard let data = image.normalized().jpegData(compressionQuality: 0.78) else {
            lastError = "사진 저장에 실패했습니다."
            return
        }

        let id = UUID()
        let fileName = "\(id.uuidString).jpg"
        do {
            try FileManager.default.createDirectory(at: photoDirectoryURL(), withIntermediateDirectories: true)
            try data.write(to: photoDirectoryURL().appendingPathComponent(fileName), options: .atomic)
            let category = mode == .fieldSurvey ? PhotoCategory.field : currentCategory
            let symbol = mode == .fieldSurvey ? nextFieldSymbol() : resolvedSymbol()
            let item = PhotoItem(
                id: id,
                category: category,
                symbol: symbol,
                memo: memo.trimmingCharacters(in: .whitespacesAndNewlines),
                fileName: fileName,
                createdAt: Date(),
                debtorName: debtorName.trimmingCharacters(in: .whitespacesAndNewlines),
                fieldSurveyor: fieldSurveyor.trimmingCharacters(in: .whitespacesAndNewlines)
            )
            photos.append(item)
            memo = ""
            save()
            advanceSymbolAfterCapture()
        } catch {
            lastError = "사진 파일을 저장할 수 없습니다."
        }
    }

    func image(for item: PhotoItem) -> UIImage? {
        UIImage(contentsOfFile: photoURL(for: item).path)
    }

    func deletePhoto(_ item: PhotoItem) {
        photos.removeAll { $0.id == item.id }
        try? FileManager.default.removeItem(at: photoURL(for: item))
        save()
        updateDefaultSymbol()
    }

    func clearCurrentPhotosOnly() {
        photos.removeAll()
        save()
        updateDefaultSymbol()
    }

    func clearAllWork() {
        photos.forEach { try? FileManager.default.removeItem(at: photoURL(for: $0)) }
        photos.removeAll()
        propertyAddress = ""
        memo = ""
        customSymbol = ""
        currentCategory = .land
        currentSymbol = "1"
        debtorName = ""
        fieldSurveyor = ""
        save()
    }

    func photoTitle(_ photo: PhotoItem) -> String {
        switch photo.category {
        case .custom:
            return "기타사항: \(photo.symbol)"
        case .field:
            return "현지답사 사진 \(photo.symbol)"
        default:
            return "\(photo.category.title) 기호 \(photo.symbol)"
        }
    }

    func photoCaption(_ photo: PhotoItem) -> String {
        if photo.category == .field {
            let debtor = photo.debtorName.isEmpty ? "" : "채무자: \(photo.debtorName)"
            let surveyor = photo.fieldSurveyor.isEmpty ? "" : "답사자: \(photo.fieldSurveyor)"
            return [debtor, surveyor, photo.memo].filter { !$0.isEmpty }.joined(separator: " / ")
        }
        return photo.memo.isEmpty ? photoTitle(photo) : photo.memo
    }

    func stampText(_ photo: PhotoItem) -> String {
        "촬영: \(Self.dateFormatter.string(from: photo.createdAt))"
    }

    func updateDefaultSymbol() {
        if mode == .fieldSurvey {
            currentSymbol = nextFieldSymbol()
            return
        }
        if currentCategory == .custom {
            currentSymbol = customSymbol.isEmpty ? "기타" : customSymbol
            return
        }
        currentSymbol = nextSymbol(for: currentCategory)
    }

    func photoURL(for item: PhotoItem) -> URL {
        photoDirectoryURL().appendingPathComponent(item.fileName)
    }

    func temporaryExportURL(fileName: String) -> URL {
        FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
    }

    private func resolvedSymbol() -> String {
        if currentCategory == .custom {
            return customSymbol.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "기타" : customSymbol
        }
        return currentSymbol
    }

    private func advanceSymbolAfterCapture() {
        if mode == .fieldSurvey {
            currentSymbol = nextFieldSymbol()
        } else if currentCategory != .custom {
            currentSymbol = nextSymbol(for: currentCategory)
        }
    }

    private func nextSymbol(for category: PhotoCategory) -> String {
        let existing = Set(photos.filter { $0.category == category }.map(\.symbol))
        let symbols: [String]
        switch category {
        case .land:
            return String((1...999).first { !existing.contains(String($0)) } ?? photos.count + 1)
        case .building:
            symbols = Self.koreanSymbols + Self.koreanSymbols.flatMap { base in (1...9).map { "\(base)-\($0)" } }
        case .extra:
            symbols = Self.consonantSymbols
        case .custom:
            return customSymbol.isEmpty ? "기타" : customSymbol
        case .field:
            return nextFieldSymbol()
        }
        return symbols.first { !existing.contains($0) } ?? "\(category.title) \(photos.count + 1)"
    }

    private func nextFieldSymbol() -> String {
        let existing = photos.filter { $0.category == .field }.count
        return String(existing + 1)
    }

    private func categoryRank(_ category: PhotoCategory) -> Int {
        switch category {
        case .land: return 0
        case .building: return 1
        case .extra: return 2
        case .custom: return 3
        case .field: return 4
        }
    }

    private func symbolRank(_ photo: PhotoItem) -> Int {
        switch photo.category {
        case .land, .field:
            return Int(photo.symbol) ?? 999
        case .building:
            return Self.koreanSymbols.firstIndex(of: photo.symbol.split(separator: "-").first.map(String.init) ?? photo.symbol) ?? 999
        case .extra:
            return Self.consonantSymbols.firstIndex(of: photo.symbol) ?? 999
        case .custom:
            return 999
        }
    }

    private func photoDirectoryURL() -> URL {
        applicationSupportURL().appendingPathComponent(photoDirectoryName, isDirectory: true)
    }

    private func stateURL() -> URL {
        applicationSupportURL().appendingPathComponent(stateFileName)
    }

    private func applicationSupportURL() -> URL {
        let url = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("AppraisalCameraIOS", isDirectory: true)
        try? FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        return url
    }

    func save() {
        let snapshot = PersistedState(
            mode: mode,
            propertyAddress: propertyAddress,
            currentCategory: currentCategory,
            currentSymbol: currentSymbol,
            customSymbol: customSymbol,
            debtorName: debtorName,
            fieldSurveyor: fieldSurveyor,
            photos: photos
        )
        do {
            let data = try JSONEncoder().encode(snapshot)
            try data.write(to: stateURL(), options: .atomic)
        } catch {
            lastError = "작업 저장에 실패했습니다."
        }
    }

    private func load() {
        guard let data = try? Data(contentsOf: stateURL()),
              let snapshot = try? JSONDecoder().decode(PersistedState.self, from: data) else {
            return
        }
        mode = snapshot.mode
        propertyAddress = snapshot.propertyAddress
        currentCategory = snapshot.currentCategory
        currentSymbol = snapshot.currentSymbol
        customSymbol = snapshot.customSymbol
        debtorName = snapshot.debtorName
        fieldSurveyor = snapshot.fieldSurveyor
        photos = snapshot.photos
    }

    private struct PersistedState: Codable {
        var mode: CaptureMode
        var propertyAddress: String
        var currentCategory: PhotoCategory
        var currentSymbol: String
        var customSymbol: String
        var debtorName: String
        var fieldSurveyor: String
        var photos: [PhotoItem]
    }

    static let koreanSymbols = ["가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하"]
    static let consonantSymbols = ["ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"]

    static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "ko_KR")
        formatter.dateFormat = "yyyy.MM.dd HH:mm"
        return formatter
    }()
}

private extension UIImage {
    func normalized() -> UIImage {
        if imageOrientation == .up { return self }
        UIGraphicsImageRenderer(size: size).image { _ in
            draw(in: CGRect(origin: .zero, size: size))
        }
    }
}
