// All amounts are integer paise (1 rupee = 100 paise), matching the API.
// Conversion is done on the text, never through floating point, so "0.1" is exactly 10 paise.

const RUPEES_PATTERN = /^(\d+)(?:\.(\d{1,2}))?$/

/**
 * Parses what the user typed in rupees ("1,23,456.7", "500", "₹ 99.99") into paise.
 * Returns null for anything that is not a non-negative amount with at most 2 decimals.
 */
export function parseRupeesToPaise(text: string): number | null {
  const cleaned = text.replace(/[\s,₹]/g, '')
  const match = RUPEES_PATTERN.exec(cleaned)
  if (!match) return null

  const rupees = match[1].replace(/^0+(?=\d)/, '')
  const fraction = (match[2] ?? '').padEnd(2, '0')
  // Above this the number is no longer exact; every API limit is far below it.
  if (rupees.length > 13) return null

  return Number(rupees + fraction)
}

const RUPEE_GROUPING = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 })

/** Formats paise as Indian rupees with 2 decimals: 12345678 -> "₹1,23,456.78", -500 -> "-₹5.00". */
export function formatPaise(paise: number): string {
  const sign = paise < 0 ? '-' : ''
  const abs = Math.abs(paise)
  const rupees = Math.trunc(abs / 100)
  const fraction = String(abs % 100).padStart(2, '0')
  return `${sign}₹${RUPEE_GROUPING.format(rupees)}.${fraction}`
}

/**
 * The 50/30/20 split exactly as the backend computes it: floor for needs and wants,
 * the remainder for savings, so the three always add up to the salary.
 */
export function splitBudget(salaryPaise: number): { needs: number; wants: number; savings: number } {
  const needs = Math.floor((salaryPaise * 50) / 100)
  const wants = Math.floor((salaryPaise * 30) / 100)
  return { needs, wants, savings: salaryPaise - needs - wants }
}
