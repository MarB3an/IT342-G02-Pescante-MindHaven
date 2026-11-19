const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const CAMPUS_DOMAIN_SUFFIXES = ['cit.edu', 'edu', 'edu.ph'];

export function isValidEmail(email) {
  return EMAIL_REGEX.test((email || '').trim());
}

export function isCampusEmail(email) {
  if (!isValidEmail(email)) return false;
  const domain = email.trim().toLowerCase().split('@')[1] || '';
  return CAMPUS_DOMAIN_SUFFIXES.some(
    (suffix) => domain === suffix || domain.endsWith(`.${suffix}`)
  );
}

export function getPasswordChecks(password) {
  const value = password || '';
  return {
    length: value.length >= 8 && value.length <= 72,
    upper: /[A-Z]/.test(value),
    lower: /[a-z]/.test(value),
    digit: /\d/.test(value),
  };
}

export function isStrongPassword(password) {
  const c = getPasswordChecks(password);
  return c.length && c.upper && c.lower && c.digit;
}

export function getDashboardPathForRole(role) {
  switch (role) {
    case 'ROLE_STUDENT':
      return '/student/dashboard';
    case 'ROLE_COUNSELOR':
      return '/counselor/dashboard';
    case 'ROLE_ADMIN':
      return '/admin/dashboard';
    default:
      return '/login';
  }
}

export const AVATAR_META = {
  sparrow: { label: 'Sparrow', icon: '🕊️' },
  willow: { label: 'Willow', icon: '🌿' },
  harbor: { label: 'Harbor', icon: '⚓' },
  lotus: { label: 'Lotus', icon: '🪷' },
  breeze: { label: 'Breeze', icon: '🍃' },
  lantern: { label: 'Lantern', icon: '🏮' },
  meadow: { label: 'Meadow', icon: '🌾' },
  aurora: { label: 'Aurora', icon: '✨' },
};
