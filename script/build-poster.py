#!/usr/bin/env python3
"""Build VKR poster content inside Lunacy frame via Automation API."""

from __future__ import annotations

import base64
import json
import mimetypes
import re
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
API = "http://localhost:31415"
FRAME_ID = "CZzIFX9Lu0SS8-ndgl88RA"
LAYERS_FILE = Path(__file__).resolve().parent / "poster-layers.json"
HERO_IMAGE = ROOT / "Attachments" / "Screenshot 2026-06-17 132841.png"
EXPORT_DIR = ROOT / "docs" / "poster"

# Palette (ARGB AARRGGBB)
C_BORDEAUX = "FF6E142C"
C_BORDEAUX_DARK = "FF3A0008"
C_NAVY = "FF1B2A3A"
C_GRAY = "FF5A6470"
C_GRAY_LIGHT = "FF8A94A0"
C_CARD = "FFF4F5F7"
C_WHITE = "FFFFFFFF"
C_BORDER = "FFD8DCE2"
C_BLUE = "FF2563EB"
C_GREEN = "FF16A34A"
C_ORANGE = "FFF97316"


class LunacyAPI:
    def __init__(self, base: str = API, timeout: float = 120.0) -> None:
        self.base = base.rstrip("/")
        self.timeout = timeout

    def _open(self, req: urllib.request.Request):
        return urllib.request.urlopen(req, timeout=self.timeout)

    def _post_raw(self, path: str, payload) -> str:
        body = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base}{path}",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with self._open(req) as resp:
            return resp.read().decode("utf-8")

    def _post_json(self, path: str, payload) -> dict | list:
        raw = self._post_raw(path, payload)
        if not raw:
            return {}
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return {"message": raw}

    def create_layers(self, layers: list[dict], parent_id: str = FRAME_ID) -> int:
        result = self._post_json(
            "/createlayers",
            {"parentId": parent_id, "layersJson": json.dumps(layers)},
        )
        if isinstance(result, dict) and "CreatedLayerIds" in result:
            return len(result["CreatedLayerIds"])
        msg = str(result.get("message", ""))
        if "Created" in msg and "successfully" in msg:
            return len(layers)
        return 0

    def find_poster_layer_ids(self) -> list[str]:
        body = {"x": 0, "y": 300, "width": 1684, "height": 2100, "maxDepth": 2}
        data = self._post_json("/getlayersinarea", body)
        if not isinstance(data, list):
            return []
        ids: list[str] = []
        for layer in data:
            name = str(layer.get("name", ""))
            pos = layer.get("pos", [0, 0])
            y = pos[1] if isinstance(pos, list) and pos else 0
            if name.startswith("poster/"):
                ids.append(layer["id"])
                continue
            # Unnamed bitmap rects produced by /insertimage in hero zone
            if (
                layer.get("_t") == "RECT"
                and not name
                and 560 <= y <= 1050
            ):
                ids.append(layer["id"])
        return ids

    def delete_layers(self, layer_ids: list[str]) -> None:
        if not layer_ids:
            return
        self._post_json("/deletelayers", layer_ids)

    def update_layers(self, layers: list[dict]) -> None:
        if layers:
            self._post_json("/updatelayers", layers)

    def insert_image(
        self,
        image_path: Path,
        x: float,
        y: float,
        parent_id: str = FRAME_ID,
        file_name: str | None = None,
    ) -> str | None:
        mime, _ = mimetypes.guess_type(str(image_path))
        mime = mime or "image/png"
        data = image_path.read_bytes()
        req = urllib.request.Request(
            f"{self.base}/insertimage",
            data=data,
            headers={
                "Content-Type": mime,
                "X-Image-Position-X": str(int(x)),
                "X-Image-Position-Y": str(int(y)),
                "X-Image-File-Name": file_name or image_path.name,
                "X-Image-Parent-Id": parent_id,
            },
            method="POST",
        )
        with self._open(req) as resp:
            raw = resp.read().decode("utf-8")
            try:
                result = json.loads(raw)
                return result.get("layerId")
            except json.JSONDecodeError:
                match = re.search(r"ID:\s*(\S+)", raw)
                return match.group(1) if match else None

    def export_layers(
        self,
        layer_ids: list[str],
        fmt: str,
        scale: float = 1.0,
        merge: bool = True,
    ) -> list[dict]:
        return self._post_json(
            "/export",
            {
                "layerIds": layer_ids,
                "format": fmt,
                "scale": scale,
                "mergeObjects": merge,
            },
        )

    def screenshot(self, layer_ids: list[str], out_path: Path) -> None:
        body = json.dumps(layer_ids).encode("utf-8")
        req = urllib.request.Request(
            f"{self.base}/getscreenshotbyid",
            data=body,
            headers={"Content-Type": "application/json"},
            method="POST",
        )
        with self._open(req) as resp:
            out_path.write_bytes(resp.read())

    def toast(self, text: str) -> None:
        try:
            self._post_json(
                "/showtoast",
                {"text": text, "iconType": 2, "priority": 5, "durationSeconds": 5},
            )
        except urllib.error.URLError:
            pass


def t(x: float, y: float, sx: float = 1.0, sy: float = 1.0) -> list[float]:
    return [sx, 0, x, 0, sy, y]


def fill(color: str) -> list[dict]:
    return [{"type": 0, "color": color}]


def rect(
    name: str,
    x: float,
    y: float,
    w: float,
    h: float,
    color: str,
    radius: float = 0,
    border: str | None = None,
) -> dict:
    layer: dict = {
        "_t": "RECT",
        "name": name,
        "transform": t(x, y),
        "size": [w, h],
        "fills": fill(color),
    }
    if radius:
        layer["cornerRadius"] = [radius, radius, radius, radius]
    if border:
        layer["borders"] = [{"color": border, "thickness": 1}]
    return layer


def text_layer(
    name: str,
    x: float,
    y: float,
    w: float,
    h: float,
    content: str,
    size: float = 24,
    color: str = C_GRAY,
    align: int = 0,
    font: str = "Arial",
    bold: bool = False,
) -> dict:
    style: dict = {
        "font": font,
        "size": size,
        "fills": fill(color),
        "align": align,
        "kerning": 0,
    }
    if bold:
        style["weight"] = 700
    return {
        "_t": "TEXT",
        "name": name,
        "transform": t(x, y),
        "size": [w, h],
        "text": content,
        "textStyle": style,
        "behavior": 1,
    }


def oval(name: str, x: float, y: float, d: float, color: str, text_color: str, num: str) -> list[dict]:
    cx = x + d / 2
    cy = y + d / 2
    return [
        {
            "_t": "OVAL",
            "name": f"{name}/circle",
            "transform": t(x, y),
            "size": [d, d],
            "fills": fill(color),
        },
        text_layer(
            f"{name}/num",
            x,
            y + d * 0.18,
            d,
            d * 0.7,
            num,
            size=d * 0.45,
            color=text_color,
            align=2,
            bold=True,
        ),
    ]


def arrow_right(name: str, x: float, y: float, length: float, color: str = C_BORDEAUX) -> list[dict]:
  h = 4
  return [
    rect(f"{name}/shaft", x, y, length - 14, h, color),
    {
      "_t": "TRIANGLE",
      "name": f"{name}/head",
      "transform": t(x + length - 14, y - 6),
      "size": [14, 16],
      "fills": fill(color),
      "rotation": 90,
    },
  ]


def section_title(name: str, x: float, y: float, w: float, title: str) -> list[dict]:
    return [
        rect(f"{name}/bar", x, y, 6, 28, C_BORDEAUX),
        text_layer(f"{name}/title", x + 16, y - 2, w - 16, 36, title, size=26, color=C_BORDEAUX_DARK, bold=True),
    ]


def build_title_layers() -> list[dict]:
    return [
        text_layer(
            "poster/subtitle",
            70,
            350,
            1544,
            40,
            "Выпускная квалификационная работа бакалавра",
            size=28,
            color=C_GRAY,
        ),
        text_layer(
            "poster/title",
            70,
            395,
            1544,
            150,
            'Рекомендательная система ранжирования автоматизированных тестов «Triage»',
            size=54,
            color=C_BORDEAUX_DARK,
            bold=True,
        ),
    ]


def build_hero_frame_layers() -> list[dict]:
    return [
        rect("poster/hero/frame", 70, 580, 1544, 450, "00000000", radius=8, border=C_BORDER),
        text_layer(
            "poster/hero/caption",
            90,
            548,
            600,
            28,
            "Интерфейс системы: просмотр и приоритизация тест-кейсов проекта B2C",
            size=20,
            color=C_GRAY_LIGHT,
        ),
    ]


def build_problem_layers() -> list[dict]:
    layers: list[dict] = []
    layers += section_title("poster/problem", 70, 1060, 690, "Проблема и цель")
    layers.append(
        rect("poster/problem/card", 70, 1105, 690, 175, C_CARD, radius=10)
    )
    layers.append(
        text_layer(
            "poster/problem/text",
            95,
            1125,
            640,
            140,
            (
                "В Zephyr Scale отсутствует приоритизация тестов внутри одного набора. "
                "Порядок выполнения формируется вручную руководителем отдела тестирования.\n\n"
                "Система Triage рекомендует порядок тестов на основе метаданных и истории "
                "провалов, формирует предлагаемый тестовый цикл и отправляет его в Zephyr Scale."
            ),
            size=20,
            color=C_NAVY,
        )
    )
    return layers


def build_stage_layers() -> list[dict]:
    stages = [
        (
            "1",
            "Получение данных из Zephyr Scale",
            "Метаданные тестов, история выполнений, статусы и приоритеты проекта B2C/B2B.",
        ),
        (
            "2",
            "Расчет весов атрибутов (LLM)",
            "Для каждого теста Ollama вычисляет весовые коэффициенты; ответ валидируется и нормируется.",
        ),
        (
            "3",
            "Сквозная AI-сортировка",
            "Второй запрос к LLM ранжирует весь набор; результат сохраняется с артефактами прослеживаемости.",
        ),
        (
            "4",
            "Формирование и отправка цикла",
            "Предлагаемый цикл (JSON) просматривается, при необходимости корректируется и отправляется в Zephyr.",
        ),
    ]
    layers: list[dict] = []
    layers += section_title("poster/stages", 70, 1305, 690, "Этапы работы системы")
    y = 1350
    for num, title, desc in stages:
        layers += oval(f"poster/stage{num}", 90, y + 12, 44, C_BORDEAUX, C_WHITE, num)
        layers.append(rect(f"poster/stage{num}/card", 150, y, 590, 100, C_WHITE, radius=8, border=C_BORDER))
        layers.append(
            text_layer(
                f"poster/stage{num}/title",
                170,
                y + 10,
                550,
                28,
                title,
                size=20,
                color=C_BORDEAUX_DARK,
                bold=True,
            )
        )
        layers.append(
            text_layer(
                f"poster/stage{num}/desc",
                170,
                y + 38,
                550,
                52,
                desc,
                size=17,
                color=C_NAVY,
            )
        )
        y += 115
    return layers


def build_architecture_layers() -> list[dict]:
    layers: list[dict] = []
    layers += section_title("poster/arch", 800, 1060, 814, "Архитектура")
    layers.append(rect("poster/arch/bg", 800, 1105, 814, 210, C_CARD, radius=10))

    boxes = [
        (820, 1145, 130, 120, C_NAVY, "Браузер\nпользователя"),
        (980, 1125, 250, 160, C_BORDEAUX, "Spring Boot\nмонолит"),
        (1270, 1135, 150, 70, C_BLUE, "Ollama\nqwen2.5"),
        (1270, 1225, 150, 70, C_ORANGE, "Zephyr\nScale API"),
    ]
    for i, (x, y, w, h, color, label) in enumerate(boxes):
        layers.append(rect(f"poster/arch/box{i}", x, y, w, h, color, radius=8))
        layers.append(
            text_layer(
                f"poster/arch/label{i}",
                x + 8,
                y + h * 0.25,
                w - 16,
                h * 0.6,
                label,
                size=17,
                color=C_WHITE,
                align=2,
                bold=True,
            )
        )

    subs = "Подсистемы: веб-интерфейс (Thymeleaf), интеграция Zephyr, интеграция LLM, приоритизация, хранение (ФС)"
    layers.append(
        text_layer("poster/arch/subs", 990, 1205, 230, 70, subs, size=14, color=C_WHITE, align=2)
    )

    layers += arrow_right("poster/arch/a1", 955, 1200, 22)
    layers += arrow_right("poster/arch/a2", 1235, 1170, 30)
    layers += arrow_right("poster/arch/a3", 1235, 1260, 30)
    return layers


def build_pipeline_layers() -> list[dict]:
    layers: list[dict] = []
    layers += section_title("poster/pipeline", 800, 1335, 814, "Конвейер приоритизации")
    layers.append(rect("poster/pipeline/bg", 800, 1380, 814, 165, C_CARD, radius=10))

    steps = [
        (830, "Веса\nатрибутов"),
        (1020, "AI-\nсортировка"),
        (1210, "Цикл\nJSON"),
        (1400, "Отправка\nв Zephyr"),
    ]
    for i, (x, label) in enumerate(steps):
        layers.append(rect(f"poster/pipe/box{i}", x, 1425, 150, 85, C_WHITE, radius=8, border=C_BORDER))
        layers.append(
            text_layer(
                f"poster/pipe/label{i}",
                x + 5,
                1445,
                140,
                60,
                label,
                size=18,
                color=C_BORDEAUX_DARK,
                align=2,
                bold=True,
            )
        )
        if i < len(steps) - 1:
            layers += arrow_right(f"poster/pipe/arr{i}", x + 155, 1465, 55)

    layers.append(
        text_layer(
            "poster/pipeline/note",
            820,
            1520,
            780,
            24,
            "Асинхронное задание (virtual threads): RUNNING -> AI_SORT -> COMPLETED",
            size=16,
            color=C_GRAY_LIGHT,
            align=2,
        )
    )
    return layers


def build_results_layers() -> list[dict]:
    layers: list[dict] = []
    layers += section_title("poster/results", 800, 1570, 814, "Результаты эксперимента (30 итераций, n=5)")
    layers.append(rect("poster/results/bg", 800, 1615, 814, 255, C_CARD, radius=10))

    # Chart area
    chart_x, chart_y, chart_w, chart_h = 840, 1660, 420, 170
    layers.append(rect("poster/results/chart", chart_x, chart_y, chart_w, chart_h, C_WHITE, radius=6, border=C_BORDER))

    metrics = [
        ("Kendall τ", 0.287, 0.453, 0.5),
        ("LCS, %", 64.0, 64.7, 100.0),
    ]
    group_w = 70
    gap = 100
    base_y = chart_y + chart_h - 20
    max_h = chart_h - 50

    for mi, (label, v3, v7, vmax) in enumerate(metrics):
        gx = chart_x + 60 + mi * (group_w * 2 + gap + 40)
        h3 = max(12, (v3 / vmax) * max_h)
        h7 = max(12, (v7 / vmax) * max_h)
        layers.append(rect(f"poster/results/b3_{mi}", gx, base_y - h3, group_w, h3, C_BORDEAUX))
        layers.append(rect(f"poster/results/b7_{mi}", gx + group_w + 12, base_y - h7, group_w, h7, C_NAVY))
        layers.append(
            text_layer(
                f"poster/results/blab_{mi}",
                gx - 10,
                base_y + 6,
                group_w * 2 + 24,
                20,
                label,
                size=14,
                color=C_GRAY,
                align=2,
            )
        )
        layers.append(
            text_layer(
                f"poster/results/v3_{mi}",
                gx,
                base_y - h3 - 22,
                group_w,
                20,
                f"{v3:.3f}" if mi == 0 else f"{v3:.0f}",
                size=13,
                color=C_BORDEAUX,
                align=2,
                bold=True,
            )
        )
        layers.append(
            text_layer(
                f"poster/results/v7_{mi}",
                gx + group_w + 12,
                base_y - h7 - 22,
                group_w,
                20,
                f"{v7:.3f}" if mi == 0 else f"{v7:.1f}",
                size=13,
                color=C_NAVY,
                align=2,
                bold=True,
            )
        )

    layers.append(
        text_layer("poster/results/leg3", chart_x + 20, chart_y + 12, 120, 20, "■ qwen2.5:3b", size=14, color=C_BORDEAUX)
    )
    layers.append(
        text_layer("poster/results/leg7", chart_x + 150, chart_y + 12, 120, 20, "■ qwen2.5:7b", size=14, color=C_NAVY)
    )

    # Stat callouts
    callouts = [
        (1290, 1645, "64%", "средняя доля LCS"),
        (1290, 1725, "0.287 / 0.453", "Kendall τ (3b / 7b)"),
        (1290, 1805, "~40 c", "среднее время цикла"),
    ]
    for i, (x, y, value, label) in enumerate(callouts):
        layers.append(rect(f"poster/callout{i}", x, y, 280, 68, C_WHITE, radius=8, border=C_BORDER))
        layers.append(
            text_layer(
                f"poster/callout{i}/val",
                x + 12,
                y + 8,
                256,
                34,
                value,
                size=28,
                color=C_BORDEAUX_DARK,
                bold=True,
                align=2,
            )
        )
        layers.append(
            text_layer(
                f"poster/callout{i}/lab",
                x + 12,
                y + 42,
                256,
                22,
                label,
                size=15,
                color=C_GRAY,
                align=2,
            )
        )

    layers.append(
        text_layer(
            "poster/results/note",
            820,
            1840,
            780,
            22,
            "τ>0: 24/30 (3b) и 30/30 (7b); LCS>50%: 28/30 и 29/30; заказчик — ООО «Теком»",
            size=15,
            color=C_GRAY_LIGHT,
            align=2,
        )
    )
    return layers


def build_stack_layers() -> list[dict]:
    layers: list[dict] = []
    layers += section_title("poster/stack", 70, 1888, 1544, "Технологический стек")
    badges = [
        (90, "Java 17+"),
        (250, "Spring Boot 4"),
        (450, "Thymeleaf SSR"),
        (650, "Gradle"),
        (820, "Ollama"),
        (980, "qwen2.5:3b / 7b"),
        (1210, "Zephyr Scale API"),
        (1430, "Git"),
    ]
    for i, (x, label) in enumerate(badges):
        w = max(130, len(label) * 11 + 30)
        layers.append(rect(f"poster/stack/badge{i}", x, 1930, w, 42, C_NAVY, radius=21))
        layers.append(
            text_layer(
                f"poster/stack/text{i}",
                x + 10,
                1938,
                w - 20,
                30,
                label,
                size=17,
                color=C_WHITE,
                align=2,
                bold=True,
            )
        )
    return layers


def build_footer_layers() -> list[dict]:
    return [
        text_layer(
            "poster/footer/student",
            900,
            2185,
            760,
            30,
            "Выполнил: студент гр. 22-ИТД-2 Подгорнов Д.С.",
            size=22,
            color=C_WHITE,
            align=2,
        ),
        text_layer(
            "poster/footer/supervisor",
            900,
            2225,
            760,
            30,
            "Научный руководитель: ________________________________",
            size=22,
            color=C_WHITE,
            align=2,
        ),
        text_layer(
            "poster/footer/direction",
            900,
            2265,
            760,
            24,
            "Направление 09.03.02 «Информационные системы и технологии»",
            size=18,
            color="CCFFFFFF",
            align=2,
        ),
    ]


def load_saved_ids(api: LunacyAPI) -> list[str]:
    saved: list[str] = []
    if LAYERS_FILE.exists():
        data = json.loads(LAYERS_FILE.read_text(encoding="utf-8"))
        saved = data.get("layerIds", [])
    discovered = api.find_poster_layer_ids()
    return list(dict.fromkeys(saved + discovered))


def save_ids(layer_ids: list[str]) -> None:
    LAYERS_FILE.write_text(
        json.dumps({"frameId": FRAME_ID, "layerIds": layer_ids}, indent=2, ensure_ascii=False),
        encoding="utf-8",
    )


def build_all_vector_layers() -> list[dict]:
    layers: list[dict] = []
    layers += build_title_layers()
    layers += build_hero_frame_layers()
    layers += build_problem_layers()
    layers += build_stage_layers()
    layers += build_architecture_layers()
    layers += build_pipeline_layers()
    layers += build_results_layers()
    layers += build_stack_layers()
    layers += build_footer_layers()
    return layers


def insert_hero_image(api: LunacyAPI) -> str | None:
    if not HERO_IMAGE.exists():
        print(f"Warning: hero image not found: {HERO_IMAGE}")
        return None
    layer_id = api.insert_image(HERO_IMAGE, x=90, y=590, parent_id=FRAME_ID)
    if layer_id:
        api.update_layers(
            [
                {
                    "id": layer_id,
                    "name": "poster/hero/image",
                    "transform": t(90, 590),
                    "size": [1504, 420],
                }
            ]
        )
    return layer_id


def export_poster(api: LunacyAPI) -> None:
    EXPORT_DIR.mkdir(parents=True, exist_ok=True)
    for fmt, ext, scale in [("PNG", "png", 1.0), ("JPG", "jpg", 1.0), ("PDF", "pdf", 1.0)]:
        try:
            results = api.export_layers([FRAME_ID], fmt, scale=scale, merge=True)
            if not results:
                continue
            item = results[0]
            out = EXPORT_DIR / f"Podgornov-VKR-Poster.{ext}"
            raw = item["data"]
            if isinstance(raw, str):
                out.write_bytes(base64.b64decode(raw))
            else:
                out.write_bytes(bytes(raw))
            print(f"Exported {out}")
        except urllib.error.URLError as exc:
            print(f"Export {fmt} failed: {exc}")

    preview = EXPORT_DIR / "Podgornov-VKR-Poster-preview.png"
    api.screenshot([FRAME_ID], preview)
    print(f"Screenshot saved {preview}")


def main() -> None:
    api = LunacyAPI()

    old_ids = load_saved_ids(api)
    if old_ids:
        print(f"Deleting {len(old_ids)} previously created layers...")
        api.delete_layers(old_ids)

    print("Creating vector layers (content-first z-order)...")
    vector_layers = build_all_vector_layers()
    vector_layers.reverse()
    total_created = 0
    batch_size = 8
    for i in range(0, len(vector_layers), batch_size):
        batch = vector_layers[i : i + batch_size]
        count = api.create_layers(batch)
        total_created += count
        print(f"  batch {i // batch_size + 1}: {count} layers")
    print(f"  total {total_created} vector layers")

    print("Inserting hero screenshot (bottom layer)...")
    hero_id = insert_hero_image(api)

    created_ids = api.find_poster_layer_ids()
    if hero_id and hero_id not in created_ids:
        created_ids.append(hero_id)

    save_ids(created_ids)
    print(f"Tracked {len(created_ids)} layer ids in {LAYERS_FILE}")

    export_poster(api)
    api.toast("Плакат Triage собран успешно")


if __name__ == "__main__":
    main()
