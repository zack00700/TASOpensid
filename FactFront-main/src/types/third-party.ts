/** A single RFC 6902 JSON Patch operation used for optimistic-lock PATCH requests. */
export interface JsonPatchOperation {
  op: 'add' | 'remove' | 'replace' | 'move' | 'copy' | 'test';
  path: string;
  value?: unknown;
  from?: string;
}

export interface ThirdParty {
  id?: string;

  /** Optimistic locking version */
  version?: number;

  /** Last update timestamp */
  updatedAt?: string;

  /** Creation timestamp */
  createdAt?: string;

     // Personal Information
  fullName: string;
  jobTitle: string;
  contactNumber: string;
  email: string;

  // Company Information
  companyName: string;
  companyAddress: string;
  industryType: string;
  companyContactPerson: string;
  companyContactEmail: string;

  // Access Information
  accessType: string;
  modulesRequired: string[];

  // Security and Compliance
  identificationType: string;
  identificationNumber: string;

  /**
   * Local field used on the client to store a JSON Patch representing
   * unsaved changes. It is not sent by the backend.
   */
  pendingPatch?: JsonPatchOperation[];
}
