export interface SessionUser {
  id: number
  username: string
  nickname: string
  role: string
}

export interface LoginResult {
  token: string
  user: SessionUser
}

export interface Page<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface Book {
  id: number
  title: string
  author: string
  coverUrl?: string | null
  price: string
  stock: number
  categoryId: number
  categoryName?: string
  description?: string | null
  isbn?: string | null
  status?: string
  synopsis?: string | null
  contents?: string | null
  excerpt?: string | null
  authorBio?: string | null
  source?: string | null
  tags?: string[]
  url?: string | null
  lemmaId?: number
}

export interface Category {
  id: number
  name: string
}

export interface CartItem {
  id: number
  bookTitle: string
  unitPrice: string
  quantity: number
  subtotal: string
}

export interface Order {
  id: number
  orderNo: string
  userId?: number
  totalAmount: string
  status: string
  createdAt: string
  itemCount: number
  items?: OrderItem[]
}

export interface OrderItem {
  id: number
  bookTitle: string
  unitPrice: string
  quantity: number
  subtotal: string
}

export interface Notice {
  id: number
  title: string
  content: string
  read: boolean
  createdAt: string
}

export interface ChatItem {
  id?: number
  localId?: string
  role: string
  content: string
  createdAt: string
}

export interface CompareStore {
  id: number
  title: string
  author: string
  price: string
  stock: number
  status: string
}

export interface CompareRow {
  query: string
  stores: CompareStore[]
  external: ExternalBook | null
  externalStatus: 'OK' | 'EMPTY' | 'UNAVAILABLE'
}

export interface ExternalBook {
  lemmaId: number
  title: string
  author?: string | null
  synopsis?: string | null
  contents?: string | null
  url?: string | null
  source?: string | null
}

export interface DeadHead {
  orderId?: number
  bizKey?: string
}
