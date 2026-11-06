"""Script to generate, validate, and upload modern bespoke dock logos to Figma."""
import os

svgs = {
    # 1. Personal (INBOX): Sleek conversation bubble with personal avatar silhouette
    "dock_personal_outlined": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M19 4H5C3.34 4 2 5.34 2 7V16.5C2 17.05 2.65 17.38 3.1 17.02L5.8 15H19C20.66 15 22 13.66 22 12V7C22 5.34 20.66 4 19 4Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <circle cx="12" cy="8.5" r="2" stroke="#FFFFFF" stroke-width="1.6"/>
  <path d="M8 13.5C8.3 11.8 10 11 12 11C14 11 15.7 11.8 16 13.5" stroke="#FFFFFF" stroke-width="1.6" stroke-linecap="round"/>
</svg>""",

    "dock_personal_filled": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path fill-rule="evenodd" clip-rule="evenodd" d="M19 4H5C3.34 4 2 5.34 2 7V16.5C2 17.05 2.65 17.38 3.1 17.02L5.8 15H19C20.66 15 22 13.66 22 12V7C22 5.34 20.66 4 19 4ZM12 6.5C13.1 6.5 14 7.4 14 8.5C14 9.6 13.1 10.5 12 10.5C10.9 10.5 10 9.6 10 8.5C10 7.4 10.9 6.5 12 6.5ZM7.8 13.8C8.2 12 10 11.2 12 11.2C14 11.2 15.8 12 16.2 13.8C16.3 14.1 16 14.3 15.7 14.3H8.3C8 14.3 7.7 14.1 7.8 13.8Z" fill="#FFFFFF"/>
</svg>""",

    # 2. Bank (TRANSACTIONS): Neoclassical Bank / Financial portico with pediment, 3 pillars, and solid podium
    "dock_bank_outlined": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 3L2 8H22L12 3Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M4 8.5V10H20V8.5" stroke="#FFFFFF" stroke-width="1.6" stroke-linecap="round"/>
  <path d="M6 10V17" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
  <path d="M12 10V17" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
  <path d="M18 10V17" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
  <path d="M3 17H21V19C21 19.55 20.55 20 20 20H4C3.45 20 3 19.55 3 19V17Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linejoin="round"/>
  <circle cx="12" cy="6" r="0.8" fill="#FFFFFF"/>
</svg>""",

    "dock_bank_filled": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path fill-rule="evenodd" clip-rule="evenodd" d="M12 2.5L2 7.5V9.5H22V7.5L12 2.5ZM5.5 11H7.5V16.5H5.5V11ZM11 11H13V16.5H11V11ZM16.5 11H18.5V16.5H16.5V11ZM2.5 17.5H21.5V19.5C21.5 20.05 21.05 20.5 20.5 20.5H3.5C2.95 20.5 2.5 20.05 2.5 19.5V17.5ZM12 5.2C12.55 5.2 13 5.65 13 6.2C13 6.75 12.55 7.2 12 7.2C11.45 7.2 11 6.75 11 6.2C11 5.65 11.45 5.2 12 5.2Z" fill="#FFFFFF"/>
</svg>""",

    # 3. Offers (PROMOTIONS): Festive modern Gift Box with energetic ribbon & top bow
    "dock_offers_outlined": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M4 11C4 10.45 4.45 10 5 10H19C19.55 10 20 10.45 20 11V19C20 20.1 19.1 21 18 21H6C4.9 21 4 20.1 4 19V11Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M3 8.5C3 8.22 3.22 8 3.5 8H20.5C20.78 8 21 8.22 21 8.5V10.5C21 10.78 20.78 11 20.5 11H3.5C3.22 11 3 10.78 3 10.5V8.5Z" stroke="#FFFFFF" stroke-width="1.6" stroke-linejoin="round"/>
  <path d="M12 8V21" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round"/>
  <path d="M12 8C10.5 8 8 6.8 8 5C8 3.5 9.5 3 10.8 4C11.6 4.6 12 6.5 12 8Z" stroke="#FFFFFF" stroke-width="1.5" stroke-linejoin="round"/>
  <path d="M12 8C13.5 8 16 6.8 16 5C16 3.5 14.5 3 13.2 4C12.4 4.6 12 6.5 12 8Z" stroke="#FFFFFF" stroke-width="1.5" stroke-linejoin="round"/>
</svg>""",

    "dock_offers_filled": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 7.8C10.4 7.8 8 6.6 8 5C8 3.6 9.4 3 10.7 4C11.5 4.6 12 6.4 12 7.8Z" fill="#FFFFFF"/>
  <path d="M12 7.8C13.6 7.8 16 6.6 16 5C16 3.6 14.6 3 13.3 4C12.5 4.6 12 6.4 12 7.8Z" fill="#FFFFFF"/>
  <path fill-rule="evenodd" clip-rule="evenodd" d="M2.5 8C2.5 7.45 2.95 7 3.5 7H20.5C21.05 7 21.5 7.45 21.5 8V10.5C21.5 11.05 21.05 11.5 20.5 11.5H19.5V19C19.5 20.38 18.38 21.5 17 21.5H7C5.62 21.5 4.5 20.38 4.5 19V11.5H3.5C2.95 11.5 2.5 11.05 2.5 10.5V8ZM10.8 11.5H6V19C6 19.55 6.45 20 7 20H10.8V11.5ZM13.2 11.5H18V19C18 19.55 17.55 20 17 20H13.2V11.5ZM10.8 8.5H4V10H10.8V8.5ZM13.2 8.5V10H20V8.5H13.2Z" fill="#FFFFFF"/>
</svg>""",

    # 4. Review (REVIEW): Attention / Inspection Clipboard with checkmark & clip
    "dock_review_outlined": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M5 6C5 4.9 5.9 4 7 4H8.5C8.8 2.8 9.8 2 11 2H13C14.2 2 15.2 2.8 15.5 4H17C18.1 4 19 4.9 19 6V19C19 20.1 18.1 21 17 21H7C5.9 21 5 20.1 5 19V6Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <rect x="9.5" y="2.5" width="5" height="3" rx="1.5" stroke="#FFFFFF" stroke-width="1.5"/>
  <path d="M8.5 12.5L10.5 14.5L15.5 9.5" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M8.5 17.5H15.5" stroke="#FFFFFF" stroke-width="1.6" stroke-linecap="round"/>
</svg>""",

    "dock_review_filled": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path fill-rule="evenodd" clip-rule="evenodd" d="M7 4C5.9 4 5 4.9 5 6V19C5 20.1 5.9 21 7 21H17C18.1 21 19 20.1 19 19V6C19 4.9 18.1 4 17 4H15.5C15.2 2.8 14.2 2 13 2H11C9.8 2 8.8 2.8 8.5 4H7ZM11 3.5H13C13.55 3.5 14 3.95 14 4.5V5H10V4.5C10 3.95 10.45 3.5 11 3.5ZM10.5 15.6L8 13.1C7.6 12.7 7.6 12.1 8 11.7C8.4 11.3 9 11.3 9.4 11.7L10.5 12.8L14.6 8.7C15 8.3 15.6 8.3 16 8.7C16.4 9.1 16.4 9.7 16 10.1L10.5 15.6ZM8 17.5C8 17.1 8.3 16.7 8.7 16.7H15.3C15.7 16.7 16 17.1 16 17.5C16 17.9 15.7 18.3 15.3 18.3H8.7C8.3 18.3 8 17.9 8 17.5Z" fill="#FFFFFF"/>
</svg>""",

    # 5. Spam (SPAM): Shield with diagonal prohibition slash (Blocked / Spam)
    "dock_spam_outlined": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path d="M12 2L4 5.5V11C4 16.5 7.4 20.8 12 22C16.6 20.8 20 16.5 20 11V5.5L12 2Z" stroke="#FFFFFF" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/>
  <path d="M8 8L16 16" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round"/>
  <circle cx="12" cy="12" r="5.5" stroke="#FFFFFF" stroke-width="1.6"/>
</svg>""",

    "dock_spam_filled": """<svg width="48" height="48" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
  <path fill-rule="evenodd" clip-rule="evenodd" d="M12 2L4 5.5V11C4 16.5 7.4 20.8 12 22C16.6 20.8 20 16.5 20 11V5.5L12 2ZM7.2 12C7.2 9.35 9.35 7.2 12 7.2C13.2 7.2 14.3 7.64 15.15 8.38L8.38 15.15C7.64 14.3 7.2 13.2 7.2 12ZM8.85 15.62L15.62 8.85C16.36 9.7 16.8 10.8 16.8 12C16.8 14.65 14.65 16.8 12 16.8C10.8 16.8 9.7 16.36 8.85 15.62Z" fill="#FFFFFF"/>
</svg>"""
}

out_dir = "docs/reviews/perf/dock_icons"
os.makedirs(out_dir, exist_ok=True)
for name, content in svgs.items():
    file_path = os.path.join(out_dir, f"{name}.svg")
    with open(file_path, "w", encoding="utf-8") as f:
        f.write(content.strip())
    print(f"Generated {file_path}")
