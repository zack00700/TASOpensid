import api from '../plugin/axios'

export interface ChargeRecord {
  id?: string
  itemId: string
  contractId?: string
  contractName?: string
  rateId?: string
  invoiceId?: string
  amount: number
  quantity: number
  uom: string
  currency: string
  calculatorUsed: string
  inputs: Record<string, unknown>
  explanation: string
  status: 'PENDING' | 'INVOICED' | 'CANCELLED'
  calculatedAt?: string
  calculatedBy?: string
}

const BASE = '/charge-records'

export const chargeRecordService = {
  async byInvoice(invoiceId: string): Promise<ChargeRecord[]> {
    const res = await api.get(`${BASE}/invoice/${encodeURIComponent(invoiceId)}`)
    return res.data
  },

  async byItem(itemId: string): Promise<ChargeRecord[]> {
    const res = await api.get(`${BASE}/item/${encodeURIComponent(itemId)}`)
    return res.data
  },
}

export default chargeRecordService
