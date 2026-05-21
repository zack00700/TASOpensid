export interface InvoiceSequence {
  id: string
  sequenceId: string
  prefix: string
  nextValue: number
  maximumDigits: number
  invoiceTypeId: string | null
  isDefault: boolean
  previewExample: string
}

export interface InvoiceSequenceForm {
  sequenceId: string
  prefix: string
  nextValue: number
  maximumDigits: number
  invoiceTypeId: string | null
}
