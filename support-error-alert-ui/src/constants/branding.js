/** Product name and user-facing copy (single source of truth). */

export const APP_NAME = 'Support Error Alert Dashboard';

/** Display font for the app title (Fractul-style geometric sans). */
export const APP_NAME_CLASS = 'app-name';

export const SUPPORT_CONTACT = {
  email: 'emailsupportd13@gmail.com',
  role: 'Support email',
};

export const SECTION = {
  JSON: {
    title: 'JSON alert',
    loadHint:
      'Choose a JSON file from your monitoring export. Only the first error in the file becomes the JSON alert.',
    emptyDetail: 'No JSON alert loaded yet. Upload a JSON file above.',
  },
  EMAIL: {
    title: 'Email alert',
    emptyDetail: 'No email alert loaded yet. Search your inbox above, then use a message as the alert.',
  },
};
