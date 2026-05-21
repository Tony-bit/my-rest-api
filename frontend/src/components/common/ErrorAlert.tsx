interface Props {
  message: string
  onDismiss?: () => void
}

export default function ErrorAlert({ message, onDismiss }: Props) {
  return (
    <div className="flex items-start gap-3 p-3 bg-red-950 border border-red-800 rounded text-red-400 text-sm">
      <svg className="w-4 h-4 mt-0.5 flex-shrink-0" fill="none" stroke="currentColor" viewBox="0 0 24 24">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
      <span className="flex-1">{message}</span>
      {onDismiss && (
        <button onClick={onDismiss} className="text-red-500 hover:text-red-400">✕</button>
      )}
    </div>
  )
}
