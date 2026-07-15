const TRANSLIT: Record<string, string> = {
  а: 'a',
  б: 'b',
  в: 'v',
  г: 'g',
  д: 'd',
  е: 'e',
  ё: 'e',
  ж: 'zh',
  з: 'z',
  и: 'i',
  й: 'y',
  к: 'k',
  л: 'l',
  м: 'm',
  н: 'n',
  о: 'o',
  п: 'p',
  р: 'r',
  с: 's',
  т: 't',
  у: 'u',
  ф: 'f',
  х: 'h',
  ц: 'ts',
  ч: 'ch',
  ш: 'sh',
  щ: 'sch',
  ъ: '',
  ы: 'y',
  ь: '',
  э: 'e',
  ю: 'yu',
  я: 'ya'
};

export function transliterateRu(input: string): string {
  return input
    .toLowerCase()
    .split('')
    .map((ch) => TRANSLIT[ch] ?? ch)
    .join('');
}

/** Lowercase latin key for fuzzy name / username matching. */
export function normalizeSearchKey(input: string): string {
  return transliterateRu(input.trim().toLowerCase().replace(/^@/, '')).replace(/[\s\-_.@]/g, '');
}

export function isValidUsername(raw: string): boolean {
  return /^[a-zA-Z0-9_]{3,32}$/.test(raw);
}

export function normalizeUsername(raw: string): string {
  return raw.trim().toLowerCase().replace(/^@/, '');
}
