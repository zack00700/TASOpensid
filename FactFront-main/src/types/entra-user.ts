export interface EntraUser {
  id: string;
  displayName: string | null;
  userPrincipalName: string | null;
  mail: string | null;
  jobTitle: string | null;
  accountEnabled: boolean;
  /** "Member" for internal employees, "Guest" for invited partners. */
  userType: string | null;
  roles: string[];
}

export interface InviteRequest {
  email: string;
  displayName?: string;
  roles?: string[];
}
