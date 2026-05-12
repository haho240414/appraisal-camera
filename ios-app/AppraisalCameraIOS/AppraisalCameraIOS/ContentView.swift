import SwiftUI
import UIKit

struct ContentView: View {
    @Bindable var store: AppState

    @State private var showingCamera = false
    @State private var showingLibrary = false
    @State private var showingPhotos = false
    @State private var showingShare = false
    @State private var showingClearAll = false
    @State private var showingClearPhotos = false
    @State private var exportItems: [Any] = []

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [Color(red: 0.05, green: 0.11, blue: 0.13), Color(red: 0.07, green: 0.20, blue: 0.22)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()

                VStack(spacing: 12) {
                    topPanel
                    Spacer(minLength: 0)
                    capturePanel
                }
                .padding(14)
            }
            .navigationTitle("답사 사진자료")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Button("PDF") { sharePDF() }
                    Button("JPG") { shareJPG() }
                    Button("목록") { showingPhotos = true }
                }
            }
        }
        .sheet(isPresented: $showingCamera) {
            CameraPicker { image in
                store.addPhoto(image)
            }
        }
        .sheet(isPresented: $showingLibrary) {
            PhotoLibraryPicker { image in
                store.addPhoto(image)
            }
        }
        .sheet(isPresented: $showingPhotos) {
            PhotoListView(
                store: store,
                showingClearPhotos: $showingClearPhotos
            )
        }
        .sheet(isPresented: $showingShare) {
            ActivityView(items: exportItems)
        }
        .alert("작업 전체 삭제", isPresented: $showingClearAll) {
            Button("삭제", role: .destructive) { store.clearAllWork() }
            Button("취소", role: .cancel) {}
        } message: {
            Text("사진과 입력된 작업 정보를 모두 삭제합니다.")
        }
        .alert("현재 사진만 전체 삭제", isPresented: $showingClearPhotos) {
            Button("삭제", role: .destructive) { store.clearCurrentPhotosOnly() }
            Button("취소", role: .cancel) {}
        } message: {
            Text("저장된 앱 설정은 유지하고 현재 사진 목록만 비웁니다.")
        }
        .alert("알림", isPresented: Binding(
            get: { store.lastError != nil },
            set: { if !$0 { store.lastError = nil } }
        )) {
            Button("확인", role: .cancel) { store.lastError = nil }
        } message: {
            Text(store.lastError ?? "")
        }
    }

    private var topPanel: some View {
        VStack(spacing: 10) {
            Picker("모드", selection: $store.mode) {
                ForEach(CaptureMode.allCases) { mode in
                    Text(mode.title).tag(mode)
                }
            }
            .pickerStyle(.segmented)
            .onChange(of: store.mode) {
                store.updateDefaultSymbol()
                store.save()
            }

            TextField("물건지 주소", text: $store.propertyAddress)
                .textInputAutocapitalization(.never)
                .padding(10)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .onChange(of: store.propertyAddress) { store.save() }

            if store.mode == .fieldSurvey {
                HStack {
                    TextField("채무자 명", text: $store.debtorName)
                    TextField("현지답사자", text: $store.fieldSurveyor)
                }
                .textInputAutocapitalization(.never)
                .padding(10)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .onChange(of: store.debtorName) { store.save() }
                .onChange(of: store.fieldSurveyor) { store.save() }
            }
        }
        .foregroundStyle(.white)
        .padding(12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
    }

    private var capturePanel: some View {
        VStack(spacing: 12) {
            if store.mode == .appraisal {
                Picker("분류", selection: $store.currentCategory) {
                    ForEach([PhotoCategory.land, .building, .extra, .custom]) { category in
                        Text(category.title).tag(category)
                    }
                }
                .pickerStyle(.segmented)
                .onChange(of: store.currentCategory) {
                    store.updateDefaultSymbol()
                    store.save()
                }

                symbolControls
            }

            TextField("사진 설명", text: $store.memo)
                .textInputAutocapitalization(.never)
                .padding(10)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))

            HStack(spacing: 10) {
                Button {
                    showingLibrary = true
                } label: {
                    Label("이미지", systemImage: "photo")
                }
                .buttonStyle(.bordered)

                Button {
                    showingCamera = true
                } label: {
                    Label("촬영", systemImage: "camera.fill")
                        .fontWeight(.bold)
                }
                .buttonStyle(.borderedProminent)

                Button(role: .destructive) {
                    showingClearAll = true
                } label: {
                    Label("전체삭제", systemImage: "trash")
                }
                .buttonStyle(.bordered)
            }

            HStack {
                Label("\(store.photos.count)장", systemImage: "doc.richtext")
                Spacer()
                Text(store.currentCategory == .custom ? store.customSymbol : store.currentSymbol)
                    .font(.headline)
                    .monospacedDigit()
            }
            .foregroundStyle(.secondary)
        }
        .padding(14)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
    }

    @ViewBuilder
    private var symbolControls: some View {
        if store.currentCategory == .custom {
            TextField("기타사항 입력", text: $store.customSymbol)
                .textInputAutocapitalization(.never)
                .padding(10)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
                .onChange(of: store.customSymbol) {
                    store.updateDefaultSymbol()
                    store.save()
                }
        } else {
            Menu {
                ForEach(symbolOptions(), id: \.self) { symbol in
                    Button(symbol) {
                        store.currentSymbol = symbol
                        store.save()
                    }
                }
            } label: {
                HStack {
                    Text("기호")
                    Spacer()
                    Text(store.currentSymbol)
                    Image(systemName: "chevron.up.chevron.down")
                }
                .padding(10)
                .background(Color.white.opacity(0.12), in: RoundedRectangle(cornerRadius: 8))
            }
        }
    }

    private func symbolOptions() -> [String] {
        switch store.currentCategory {
        case .land:
            return (1...80).map(String.init)
        case .building:
            return AppState.koreanSymbols + AppState.koreanSymbols.flatMap { base in (1...9).map { "\(base)-\($0)" } }
        case .extra:
            return AppState.consonantSymbols
        case .custom, .field:
            return []
        }
    }

    private func sharePDF() {
        do {
            let url = try ExportService.makePDF(store: store)
            exportItems = [url]
            showingShare = true
        } catch {
            store.lastError = "PDF 생성에 실패했습니다."
        }
    }

    private func shareJPG() {
        do {
            let urls = try ExportService.makeJPGPages(store: store)
            exportItems = urls
            showingShare = true
        } catch {
            store.lastError = "JPG 생성에 실패했습니다."
        }
    }
}

struct PhotoListView: View {
    @Bindable var store: AppState
    @Binding var showingClearPhotos: Bool
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button(role: .destructive) {
                        dismiss()
                        showingClearPhotos = true
                    } label: {
                        Label("현재 사진만 전체 삭제", systemImage: "trash")
                    }
                }

                ForEach(store.sortedPhotos) { photo in
                    HStack(spacing: 12) {
                        if let image = store.image(for: photo) {
                            Image(uiImage: image)
                                .resizable()
                                .scaledToFill()
                                .frame(width: 58, height: 58)
                                .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text(store.photoTitle(photo))
                                .font(.headline)
                            Text(store.photoCaption(photo))
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                                .lineLimit(2)
                            Text(store.stampText(photo))
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .onDelete { offsets in
                    let sorted = store.sortedPhotos
                    offsets.map { sorted[$0] }.forEach(store.deletePhoto)
                }
            }
            .navigationTitle("사진 자료")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("닫기") { dismiss() }
                }
            }
        }
    }
}

struct ActivityView: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}

#Preview {
    ContentView(store: AppState())
}
