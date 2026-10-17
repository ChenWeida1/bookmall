import dayjs from 'dayjs'

/** 订单、消息和客服的时间，按浏览器本地时区。 */
export function formatTime(value?: string | null) {
  if (!value) {
    return ''
  }
  const parsed = dayjs(value)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD HH:mm:ss') : value
}

export function statusLabel(status?: string | null) {
  if (status === 'PENDING_PAY') return '待支付'
  if (status === 'PAID') return '已支付'
  if (status === 'CANCELLED') return '已取消'
  if (status === 'ON_SALE') return '在售'
  if (status === 'OFF_SALE') return '下架'
  if (status === 'ZHIPU') return '客服模型整理'
  if (status === 'BAIDU') return '百度百科'
  if (status === 'ADMIN') return '管理员编写'
  return status || ''
}
