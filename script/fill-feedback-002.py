#!/usr/bin/env python3
"""Fill Podgornov-VKR-Feedback-0.0.2.docx with supervisor review text and FGOS table."""

from __future__ import annotations

from copy import deepcopy
from pathlib import Path

from docx import Document
from docx.oxml.ns import qn
from docx.text.paragraph import Paragraph

NBSP = "\u00a0"

DOCX_PATH = Path("docs/Podgornov-VKR-Feedback/docx/Podgornov-VKR-Feedback-0.0.2.docx")

# Column index in the FGOS table: 1=*, 2=2, 3=3, 4=4, 5=5
TABLE_GRADES = [5, 5, 4, 5, 5, 5, 4, 5]

BODY_PARAGRAPHS = [
    (
        "Выпускная квалификационная работа студента Подгорнова Данилы Сергеевича "
        "на тему «Разработка рекомендательной системы ранжирования автоматизированных "
        "тестов» посвящена решению актуальной задачи формирования оптимального порядка "
        "выполнения тестов при подготовке регрессионного набора. С ростом сложности "
        "информационных систем и числа тестов при ограниченных ресурсах возрастает "
        "потребность в автоматизации приоритизации. Система управления тестами "
        f"Smart{NBSP}Bear Zephyr{NBSP}Scale, применяемая в ООО{NBSP}«Теком», "
        "не предоставляет встроенного средства упорядочивания тестов в пределах одного "
        "набора, что увеличивает трудозатраты и снижает вероятность раннего выявления "
        "дефектов."
    ),
    (
        "Объем и качество выполненной работы соответствуют требованиям, предъявляемым "
        "к выпускной квалификационной работе бакалавра. Пояснительная записка содержит "
        "54 страницы, шестнадцать иллюстраций, шесть листингов программного кода "
        "и двенадцать источников. По результатам проверки в системе «Антиплагиат» "
        "оригинальность текста составляет 98,89%. Основным результатом стала "
        "информационная система «Triage» – монолитное клиент‑серверное веб‑приложение "
        f"на языке Java с использованием Spring{NBSP}Boot и серверной отрисовки страниц. "
        "Система интегрирована с Zephyr Scale и сервисом Ollama, реализует двухэтапное "
        "ранжирование тестов средствами локальной большой языковой модели, формирует "
        "предлагаемый тестовый цикл и отправляет его во внешнюю систему управления "
        "тестами. Качество программного кода подтверждается набором из двадцати трех "
        "модульных тестов."
    ),
    (
        "К положительным сторонам работы следует отнести обоснованный выбор архитектуры "
        "и инструментальных средств, разработку технического задания в соответствии "
        "с ГОСТ 34.602‑2020, а также применение локальной большой языковой модели, "
        "при котором метаданные тестов и история их выполнений не покидают "
        "инфраструктуру организации‑заказчика. Студентом предложена и реализована "
        "двухэтапная схема ранжирования с динамическим расчетом весовых коэффициентов "
        "атрибутов и последующей сквозной AI‑сортировкой. Качество получаемого порядка "
        "объективно оценено коэффициентом согласованности Kendall τ и долей LCS "
        "относительно эталонной последовательности. Целевые показатели достигнуты: "
        "время формирования регрессионного набора сократилось на 23%, доля дефектов, "
        "выявленных в первой четверти набора, возросла на 7%. Разработанный "
        "пользовательский интерфейс признан интуитивно понятным представителями "
        f"целевой аудитории ООО{NBSP}«Теком». Перечень функций технического задания "
        "реализован полностью."
    ),
    (
        "К недостаткам работы следует отнести ограниченный масштаб экспериментальной "
        "оценки качества ранжирования: сопоставление с эталоном проводилось на наборе "
        "из пяти тестов одного проекта. Для модели qwen2.5:3b в отдельных итерациях "
        "наблюдалась нестабильность результата, что отражено в разбросе значений "
        "Kendall τ. Хранение результатов организовано в файловой системе без "
        "использования реляционной базы данных и без разграничения доступа пользователей. "
        "Перечисленные замечания носят характер направлений дальнейшего развития "
        "и не оказывают существенного влияния на общую оценку выпускной квалификационной "
        "работы."
    ),
    (
        "В процессе выполнения работы студент продемонстрировал высокую степень "
        "самостоятельности, точно следовал указаниям руководителя по усовершенствованию "
        "и корректировке материалов, а также предлагал собственные варианты решения "
        "возникающих проблем. Подгорнов Д.С. показал достаточную теоретическую "
        "подготовку в области информационных систем и технологий, владение современными "
        "средствами разработки и умение рационально решать практические вопросы: "
        "настройку интеграции с внешними API, валидацию ответов языковой модели, "
        "организацию тестирования и оценку качества результата по количественным метрикам."
    ),
    (
        "Считаю, что работа Подгорнова Д.С. соответствует требованиям, предъявляемым "
        "к выпускным квалификационным работам бакалавров по направлению подготовки "
        "09.03.02 «Информационные системы и технологии», и заслуживает оценки "
        "«отлично», а ее автор – присвоения квалификации «бакалавр» по направлению "
        "09.03.02 «Информационные системы и технологии»."
    ),
]


def set_paragraph_text(paragraph: Paragraph, text: str) -> None:
    """Replace paragraph text while keeping the first run's formatting if possible."""
    if paragraph.runs:
        paragraph.runs[0].text = text
        for run in paragraph.runs[1:]:
            run.text = ""
    else:
        paragraph.add_run(text)


def insert_paragraph_after(paragraph: Paragraph, text: str, style: str | None = None) -> Paragraph:
  """Insert a new paragraph immediately after the given one."""
  new_p = deepcopy(paragraph._p)
  paragraph._p.addnext(new_p)
  new_para = Paragraph(new_p, paragraph._parent)
  if style:
    new_para.style = style
  set_paragraph_text(new_para, text)
  return new_para


def find_body_placeholder_indices(paragraphs) -> list[int]:
  indices = []
  for i, para in enumerate(paragraphs):
    text = para.text.strip()
    if "…" in text or text.endswith("..."):
      if "Подгорнов" in text or "Студентом" in text or "технического задания" in text:
        indices.append(i)
  return indices


def fill_body(doc: Document) -> None:
  placeholders = find_body_placeholder_indices(doc.paragraphs)
  if len(placeholders) < 3:
    raise RuntimeError(f"Expected 3 placeholder paragraphs, found {len(placeholders)}")

  style_name = doc.paragraphs[placeholders[0]].style.name

  set_paragraph_text(doc.paragraphs[placeholders[0]], BODY_PARAGRAPHS[0])
  set_paragraph_text(doc.paragraphs[placeholders[1]], BODY_PARAGRAPHS[1])
  set_paragraph_text(doc.paragraphs[placeholders[2]], BODY_PARAGRAPHS[2])

  anchor = doc.paragraphs[placeholders[2]]
  for extra_text in BODY_PARAGRAPHS[3:]:
    anchor = insert_paragraph_after(anchor, extra_text, style_name)


def fill_table(doc: Document) -> None:
  if not doc.tables:
    raise RuntimeError("FGOS table not found")

  table = doc.tables[0]
  for row_idx, grade in enumerate(TABLE_GRADES, start=2):
    if row_idx >= len(table.rows):
      raise RuntimeError(f"Table row {row_idx} missing")
    for col in range(1, 6):
      table.rows[row_idx].cells[col].text = ""
    table.rows[row_idx].cells[grade].text = "+"


def main() -> None:
  doc = Document(DOCX_PATH)
  fill_body(doc)
  fill_table(doc)
  doc.save(DOCX_PATH)
  print(f"Updated: {DOCX_PATH}")


if __name__ == "__main__":
  main()
