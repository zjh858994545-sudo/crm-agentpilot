import { Alert, Button, Space, Typography } from 'antd';

const { Text } = Typography;

type ApiErrorNoticeProps = {
  error: string;
  title?: string;
  onRetry?: () => void;
};

function splitTraceId(message: string) {
  const marker = 'Trace ID：';
  const index = message.indexOf(marker);
  if (index < 0) {
    return { message, traceId: '' };
  }
  return {
    message: message.slice(0, index).trim(),
    traceId: message.slice(index + marker.length).trim()
  };
}

export default function ApiErrorNotice({ error, title = '操作暂时无法完成', onRetry }: ApiErrorNoticeProps) {
  const detail = splitTraceId(error);
  return (
    <Alert
      type="warning"
      showIcon
      message={title}
      description={
        <Space direction="vertical" size={6}>
          <Text>{detail.message || error}</Text>
          {detail.traceId ? (
            <Text type="secondary">
              Trace ID：<Text code>{detail.traceId}</Text>
            </Text>
          ) : null}
          {onRetry ? (
            <Button size="small" onClick={onRetry}>
              重试
            </Button>
          ) : null}
        </Space>
      }
    />
  );
}
