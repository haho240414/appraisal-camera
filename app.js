const CATEGORY_ORDER = ["land", "building", "extra"];
const CATEGORY_LABELS = {
  land: "토지",
  building: "건물",
  extra: "제시외건물",
};

const LAND_SYMBOLS = Array.from({ length: 99 }, (_, index) => String(index + 1));
const BUILDING_SYMBOLS = ["가", "나", "다", "라", "마", "바", "사", "아", "자", "차", "카", "타", "파", "하"];
const EXTRA_SYMBOLS = ["ㄱ", "ㄴ", "ㄷ", "ㄹ", "ㅁ", "ㅂ", "ㅅ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ"];
const BUILDING_SUBS = Array.from({ length: 9 }, (_, index) => String(index + 1));
const STORAGE_KEY = "appraisal-camera-photos-v1";

const video = document.querySelector("#cameraVideo");
const canvas = document.querySelector("#captureCanvas");
const cameraEmpty = document.querySelector("#cameraEmpty");
const statusText = document.querySelector("#statusText");
const symbolSelect = document.querySelector("#symbolSelect");
const buildingSubSelect = document.querySelector("#buildingSubSelect");
const buildingSubfield = document.querySelector("#buildingSubfield");
const memoInput = document.querySelector("#memoInput");
const captureButton = document.querySelector("#captureButton");
const fileInput = document.querySelector("#fileInput");
const photoList = document.querySelector("#photoList");
const countText = document.querySelector("#countText");
const printButton = document.querySelector("#printButton");
const clearButton = document.querySelector("#clearButton");

let photos = loadPhotos();
let currentCategory = "land";

function loadPhotos() {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY)) || [];
  } catch {
    return [];
  }
}

function makeId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function savePhotos() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(photos));
  } catch {
    statusText.textContent = "사진 용량이 커서 자동 저장은 건너뛰었지만, 현재 출력자료에는 유지됩니다.";
  }
}

function getCategorySymbols(category) {
  if (category === "land") return LAND_SYMBOLS;
  if (category === "building") return BUILDING_SYMBOLS;
  return EXTRA_SYMBOLS;
}

function makeSymbol(category, base, sub = "") {
  if (category === "building" && sub) return `${base}-${sub}`;
  return base;
}

function splitBuildingSymbol(symbol) {
  const [base, sub = ""] = symbol.split("-");
  return { base, sub };
}

function symbolRank(photo) {
  if (photo.category === "land") {
    return Number(photo.symbol) || 9999;
  }

  if (photo.category === "building") {
    const { base, sub } = splitBuildingSymbol(photo.symbol);
    const baseIndex = BUILDING_SYMBOLS.indexOf(base);
    return (baseIndex < 0 ? 999 : baseIndex) * 100 + (Number(sub) || 0);
  }

  const extraIndex = EXTRA_SYMBOLS.indexOf(photo.symbol);
  return extraIndex < 0 ? 9999 : extraIndex;
}

function orderedPhotos() {
  return [...photos].sort((a, b) => {
    const categoryDiff = CATEGORY_ORDER.indexOf(a.category) - CATEGORY_ORDER.indexOf(b.category);
    if (categoryDiff) return categoryDiff;

    const symbolDiff = symbolRank(a) - symbolRank(b);
    if (symbolDiff) return symbolDiff;

    return a.createdAt - b.createdAt;
  });
}

function nextSymbol(category) {
  const used = new Set(photos.filter((photo) => photo.category === category).map((photo) => photo.symbol));

  if (category === "building") {
    for (const symbol of BUILDING_SYMBOLS) {
      if (!used.has(symbol)) return { base: symbol, sub: "" };
    }
    return { base: BUILDING_SYMBOLS[BUILDING_SYMBOLS.length - 1], sub: "1" };
  }

  const symbols = getCategorySymbols(category);
  return symbols.find((symbol) => !used.has(symbol)) || symbols[symbols.length - 1];
}

function populateSymbolControls() {
  symbolSelect.innerHTML = "";
  for (const symbol of getCategorySymbols(currentCategory)) {
    const option = document.createElement("option");
    option.value = symbol;
    option.textContent = `${CATEGORY_LABELS[currentCategory]} 기호 ${symbol}`;
    symbolSelect.append(option);
  }

  buildingSubSelect.innerHTML = "";
  const none = document.createElement("option");
  none.value = "";
  none.textContent = "없음";
  buildingSubSelect.append(none);
  for (const sub of BUILDING_SUBS) {
    const option = document.createElement("option");
    option.value = sub;
    option.textContent = `-${sub}`;
    buildingSubSelect.append(option);
  }

  buildingSubfield.classList.toggle("is-hidden", currentCategory !== "building");

  const upcoming = nextSymbol(currentCategory);
  if (currentCategory === "building") {
    symbolSelect.value = upcoming.base;
    buildingSubSelect.value = upcoming.sub;
  } else {
    symbolSelect.value = upcoming;
  }
}

function selectedSymbol() {
  return makeSymbol(currentCategory, symbolSelect.value, buildingSubSelect.value);
}

function updateCategory(category) {
  currentCategory = category;
  populateSymbolControls();
}

async function startCamera() {
  if (!navigator.mediaDevices?.getUserMedia) {
    statusText.textContent = "이 브라우저에서는 직접 촬영을 지원하지 않습니다.";
    return;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: { ideal: "environment" } },
      audio: false,
    });
    video.srcObject = stream;
    cameraEmpty.classList.add("is-hidden");
    statusText.textContent = "촬영할 대상을 선택한 뒤 등록하세요.";
  } catch {
    statusText.textContent = "카메라 권한이 없어서 이미지 선택으로 등록할 수 있습니다.";
  }
}

function addPhoto(imageData) {
  const symbol = selectedSymbol();
  photos.push({
    id: makeId(),
    category: currentCategory,
    symbol,
    memo: memoInput.value.trim(),
    imageData,
    createdAt: Date.now(),
  });

  memoInput.value = "";
  savePhotos();
  renderPhotos();
  populateSymbolControls();
  return symbol;
}

function capturePhoto() {
  if (!video.videoWidth || !video.videoHeight) {
    statusText.textContent = "카메라 준비가 끝나지 않았습니다. 잠시 후 다시 시도하세요.";
    return;
  }

  const maxWidth = 1280;
  const scale = Math.min(1, maxWidth / video.videoWidth);
  canvas.width = Math.round(video.videoWidth * scale);
  canvas.height = Math.round(video.videoHeight * scale);
  const context = canvas.getContext("2d");
  context.drawImage(video, 0, 0, canvas.width, canvas.height);
  const symbol = addPhoto(canvas.toDataURL("image/jpeg", 0.88));
  statusText.textContent = `${CATEGORY_LABELS[currentCategory]} 기호 ${symbol} 사진을 등록했습니다.`;
}

function readSelectedFile(file) {
  if (!file) return;
  const reader = new FileReader();
  reader.onload = () => {
    const symbol = addPhoto(reader.result);
    statusText.textContent = `${CATEGORY_LABELS[currentCategory]} 기호 ${symbol} 이미지를 등록했습니다.`;
    fileInput.value = "";
  };
  reader.readAsDataURL(file);
}

function deletePhoto(id) {
  photos = photos.filter((photo) => photo.id !== id);
  savePhotos();
  renderPhotos();
  populateSymbolControls();
}

function clearPhotos() {
  if (!photos.length) return;
  const ok = window.confirm("등록된 사진을 모두 삭제할까요?");
  if (!ok) return;
  photos = [];
  savePhotos();
  renderPhotos();
  populateSymbolControls();
}

function photoTitle(photo) {
  return `${CATEGORY_LABELS[photo.category]} 기호 ${photo.symbol}`;
}

function renderPhotos() {
  photoList.innerHTML = "";
  countText.textContent = `등록된 사진 ${photos.length}장`;

  if (!photos.length) {
    const empty = document.createElement("div");
    empty.className = "empty-state";
    empty.textContent = "촬영한 사진이 이곳에 토지, 건물, 제시외건물 순서로 정리됩니다.";
    photoList.append(empty);
    return;
  }

  for (const category of CATEGORY_ORDER) {
    const categoryPhotos = orderedPhotos().filter((photo) => photo.category === category);
    if (!categoryPhotos.length) continue;

    const group = document.createElement("section");
    group.className = "group";

    const title = document.createElement("div");
    title.className = "group-title";
    title.innerHTML = `<h3>${CATEGORY_LABELS[category]}</h3><span>${categoryPhotos.length}장</span>`;

    const grid = document.createElement("div");
    grid.className = "photo-grid";

    for (const photo of categoryPhotos) {
      const card = document.createElement("article");
      card.className = "photo-card";

      const img = document.createElement("img");
      img.src = photo.imageData;
      img.alt = photoTitle(photo);

      const meta = document.createElement("div");
      meta.className = "photo-meta";

      const titleText = document.createElement("strong");
      titleText.textContent = photoTitle(photo);

      const memo = document.createElement("span");
      memo.textContent = photo.memo || new Date(photo.createdAt).toLocaleString("ko-KR");

      const actions = document.createElement("div");
      actions.className = "card-actions";

      const deleteButton = document.createElement("button");
      deleteButton.className = "delete-button";
      deleteButton.type = "button";
      deleteButton.textContent = "삭제";
      deleteButton.addEventListener("click", () => deletePhoto(photo.id));

      meta.append(titleText, memo);
      actions.append(deleteButton);
      card.append(img, meta, actions);
      grid.append(card);
    }

    group.append(title, grid);
    photoList.append(group);
  }
}

document.querySelectorAll("input[name='category']").forEach((input) => {
  input.addEventListener("change", () => updateCategory(input.value));
});

captureButton.addEventListener("click", capturePhoto);
fileInput.addEventListener("change", () => readSelectedFile(fileInput.files[0]));
printButton.addEventListener("click", () => window.print());
clearButton.addEventListener("click", clearPhotos);

populateSymbolControls();
renderPhotos();
startCamera();
