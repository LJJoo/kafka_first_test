import { useState, useEffect, useRef } from 'react';
import {
  createOrder, getProducerOrders, getConsumerOrders,
  pauseProducer, resumeProducer, getProducerStatus,
  getProducerBuffer, clearProducerBuffer,
  pauseConsumer, resumeConsumer, getConsumerStatus,
} from './api/orderApi';
import './App.css';

const RANDOM_PRODUCTS = ['사과', '바나나', '노트북', '마우스', '키보드', '모니터', '헤드셋', '책상', '의자', '충전기'];
const PAGE_SIZE = 10;
const STATUS_TABS = ['전체', 'PENDING', 'KAFKA_SENT', 'PROCESSED', 'FAILED'];

const DEFAULT_BOOTSTRAP = '192.168.153.128:9092';

const getKafkaCommands = (server) => [
  {
    category: '토픽 확인',
    items: [
      {
        label: '토픽 목록',
        cmd: `bin/kafka-topics.sh --bootstrap-server ${server} --list`,
      },
      {
        label: '토픽 상세 (파티션 / 리더)',
        cmd: `bin/kafka-topics.sh --bootstrap-server ${server} --describe --topic order-events`,
      },
    ],
  },
  {
    category: '메시지 구독',
    items: [
      {
        label: '실시간 수신',
        cmd: `bin/kafka-console-consumer.sh --bootstrap-server ${server} --topic order-events`,
      },
      {
        label: '처음부터 전체 수신',
        cmd: `bin/kafka-console-consumer.sh --bootstrap-server ${server} --topic order-events --from-beginning`,
      },
      {
        label: '파티션 0만 수신',
        cmd: `bin/kafka-console-consumer.sh --bootstrap-server ${server} --topic order-events --partition 0 --from-beginning`,
      },
      {
        label: 'Key 포함 출력',
        cmd: `bin/kafka-console-consumer.sh --bootstrap-server ${server} --topic order-events --from-beginning --property print.key=true`,
      },
    ],
  },
  {
    category: '메시지 직접 발행',
    items: [
      {
        label: '직접 발행',
        cmd: `bin/kafka-console-producer.sh --bootstrap-server ${server} --topic order-events`,
      },
      {
        label: 'Key 지정 발행',
        cmd: `bin/kafka-console-producer.sh --bootstrap-server ${server} --topic order-events --property "key.separator=:" --property "parse.key=true"`,
      },
    ],
  },
  {
    category: 'Consumer Group / LAG',
    items: [
      {
        label: 'Group 목록',
        cmd: `bin/kafka-consumer-groups.sh --bootstrap-server ${server} --list`,
      },
      {
        label: 'LAG 상세 조회',
        cmd: `bin/kafka-consumer-groups.sh --bootstrap-server ${server} --describe --group order-group`,
      },
    ],
  },
  {
    category: 'Offset 리셋 (재처리)',
    items: [
      {
        label: '처음부터 재처리',
        cmd: `bin/kafka-consumer-groups.sh --bootstrap-server ${server} --group order-group --topic order-events --reset-offsets --to-earliest --execute`,
      },
      {
        label: 'Offset 지정 이동',
        cmd: `bin/kafka-consumer-groups.sh --bootstrap-server ${server} --group order-group --topic order-events --reset-offsets --to-offset 5 --execute`,
      },
    ],
  },
];

function randomOrder() {
  return {
    product: RANDOM_PRODUCTS[Math.floor(Math.random() * RANDOM_PRODUCTS.length)],
    quantity: Math.floor(Math.random() * 9) + 1,
    price: (Math.floor(Math.random() * 100) + 1) * 1000,
  };
}

function Pagination({ page, total, onChange }) {
  if (total <= 1) return null;
  return (
    <div className="pagination">
      <button onClick={() => onChange(1)} disabled={page === 1}>«</button>
      <button onClick={() => onChange(page - 1)} disabled={page === 1}>‹</button>
      <span className="page-info">{page} / {total}</span>
      <button onClick={() => onChange(page + 1)} disabled={page === total}>›</button>
      <button onClick={() => onChange(total)} disabled={page === total}>»</button>
    </div>
  );
}

function App() {
  const [form, setForm] = useState({ product: '', quantity: '', price: '' });
  const [randomCount, setRandomCount] = useState(1);
  const [producerOrders, setProducerOrders] = useState([]);
  const [consumerOrders, setConsumerOrders] = useState([]);
  const [message, setMessage] = useState('');
  const [producerPaused, setProducerPaused] = useState(false);
  const [consumerPaused, setConsumerPaused] = useState(false);
  const [lag, setLag] = useState(0);
  const [buffer, setBuffer] = useState({ count: 0, messages: [] });
  const [producerFilter, setProducerFilter] = useState('전체');
  const [producerPage, setProducerPage] = useState(1);
  const [consumerPage, setConsumerPage] = useState(1);
  const [copiedCmd, setCopiedCmd] = useState(null);
  const [bootstrapServer, setBootstrapServer] = useState(DEFAULT_BOOTSTRAP);
  const kafkaCommands = getKafkaCommands(bootstrapServer);
  const intervalRef = useRef(null);

  // 파생 상태 — Producer
  const filteredProducer = producerFilter === '전체'
    ? producerOrders
    : producerOrders.filter(o => o.status === producerFilter);
  const sortedProducer = [...filteredProducer].sort((a, b) => b.id - a.id);
  const totalProducerPages = Math.max(1, Math.ceil(sortedProducer.length / PAGE_SIZE));
  const pagedProducer = sortedProducer.slice((producerPage - 1) * PAGE_SIZE, producerPage * PAGE_SIZE);

  // 파생 상태 — Consumer
  const sortedConsumer = [...consumerOrders].sort((a, b) => b.id - a.id);
  const totalConsumerPages = Math.max(1, Math.ceil(sortedConsumer.length / PAGE_SIZE));
  const pagedConsumer = sortedConsumer.slice((consumerPage - 1) * PAGE_SIZE, consumerPage * PAGE_SIZE);

  const fetchOrders = async () => {
    try {
      const [p, c] = await Promise.all([getProducerOrders(), getConsumerOrders()]);
      setProducerOrders(p.data);
      setConsumerOrders(c.data);
    } catch (e) {
      console.error('주문 조회 실패', e);
    }
  };

  const fetchStatus = async () => {
    try {
      const [p, c, buf] = await Promise.all([getProducerStatus(), getConsumerStatus(), getProducerBuffer()]);
      setProducerPaused(p.data.paused);
      setConsumerPaused(c.data.paused);
      setLag(c.data.lag);
      setBuffer(buf.data);
    } catch (e) {
      console.error('상태 조회 실패', e);
    }
  };

  useEffect(() => {
    fetchOrders();
    fetchStatus();
    intervalRef.current = setInterval(() => {
      fetchStatus();
      fetchOrders();
    }, 3000);
    return () => clearInterval(intervalRef.current);
  }, []);

  const handleFilterChange = (tab) => {
    setProducerFilter(tab);
    setProducerPage(1);
  };

  const handleCopy = (cmd) => {
    navigator.clipboard.writeText(cmd);
    setCopiedCmd(cmd);
    setTimeout(() => setCopiedCmd(null), 1500);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      await createOrder({
        product: form.product,
        quantity: Number(form.quantity),
        price: Number(form.price),
      });
      setMessage('주문이 전송되었습니다.');
      setForm({ product: '', quantity: '', price: '' });
      setTimeout(() => { fetchOrders(); fetchStatus(); setMessage(''); }, 500);
    } catch (e) {
      setMessage('주문 전송 실패: ' + (e.response?.data || e.message));
    }
  };

  const handleRandom = async () => {
    try {
      await Promise.all(Array.from({ length: randomCount }, () => createOrder(randomOrder())));
      setMessage(`랜덤 주문 ${randomCount}개 전송 완료`);
      setTimeout(() => { fetchOrders(); fetchStatus(); setMessage(''); }, 500);
    } catch (e) {
      setMessage('랜덤 전송 실패: ' + (e.response?.data || e.message));
    }
  };

  const handleProducerToggle = async () => {
    try {
      const res = producerPaused ? await resumeProducer() : await pauseProducer();
      setProducerPaused(res.data.paused);
      setTimeout(() => { fetchOrders(); fetchStatus(); }, 500);
    } catch (e) {
      console.error('Producer 제어 실패', e);
    }
  };

  const handleConsumerToggle = async () => {
    try {
      const res = consumerPaused ? await resumeConsumer() : await pauseConsumer();
      setConsumerPaused(res.data.paused);
      setLag(res.data.lag);
    } catch (e) {
      console.error('Consumer 제어 실패', e);
    }
  };

  const handleClearBuffer = async () => {
    try {
      const res = await clearProducerBuffer();
      setMessage(`Redis 버퍼 ${res.data.cleared}건 삭제됨`);
      setTimeout(() => { fetchStatus(); setMessage(''); }, 1000);
    } catch (e) {
      console.error('버퍼 삭제 실패', e);
    }
  };

  return (
    <div className="app-layout">

      {/* ── 왼쪽: 메인 콘텐츠 ── */}
      <div className="main-col">
        <h1>Kafka 주문 테스트</h1>

        {/* 제어판 */}
        <section className="card">
          <h2>Kafka 제어판</h2>
          <div className="control-panel">
            <div className="control-item">
              <span className="control-label">Producer (test1)</span>
              <span className={`badge ${producerPaused ? 'paused' : 'running'}`}>
                {producerPaused ? '중지됨' : '실행 중'}
              </span>
              <button
                className={producerPaused ? 'btn-resume' : 'btn-pause'}
                onClick={handleProducerToggle}
              >
                {producerPaused ? '재개' : '중지'}
              </button>
            </div>
            <div className="control-item">
              <span className="control-label">Consumer (test2)</span>
              <span className={`badge ${consumerPaused ? 'paused' : 'running'}`}>
                {consumerPaused ? '중지됨' : '실행 중'}
              </span>
              <button
                className={consumerPaused ? 'btn-resume' : 'btn-pause'}
                onClick={handleConsumerToggle}
              >
                {consumerPaused ? '재개' : '중지'}
              </button>
              <span className="lag-badge">
                미처리 메시지 (LAG): <strong>{lag >= 0 ? lag : '조회 실패'}</strong>
              </span>
            </div>
          </div>

          {/* Redis 버퍼 */}
          <div className="buffer-panel">
            <div className="buffer-header">
              <span className="buffer-title">
                Redis 버퍼 대기 <strong>{buffer.count}건</strong>
              </span>
              {buffer.count > 0 && (
                <button className="btn-clear" onClick={handleClearBuffer}>버퍼 비우기</button>
              )}
            </div>
            {buffer.count > 0 ? (
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr><th>주문 ID</th><th>상품</th><th>수량</th><th>가격</th></tr>
                  </thead>
                  <tbody>
                    {buffer.messages.map((m, i) => (
                      <tr key={i}>
                        <td>{m.orderId}</td>
                        <td>{m.product}</td>
                        <td>{m.quantity}</td>
                        <td>{m.price?.toLocaleString()}원</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <p className="empty-state">대기 중인 메시지 없음</p>
            )}
          </div>
        </section>

        {/* 주문 생성 */}
        <section className="card">
          <h2>주문 생성</h2>
          <form onSubmit={handleSubmit}>
            <input
              placeholder="상품명"
              value={form.product}
              onChange={(e) => setForm({ ...form, product: e.target.value })}
              required
            />
            <input
              type="number"
              placeholder="수량"
              value={form.quantity}
              onChange={(e) => setForm({ ...form, quantity: e.target.value })}
              required
              min="1"
            />
            <input
              type="number"
              placeholder="가격"
              value={form.price}
              onChange={(e) => setForm({ ...form, price: e.target.value })}
              required
              min="0"
            />
            <button type="submit">주문 전송</button>
          </form>
          {message && <p className="message">{message}</p>}
          <div className="random-row">
            <span>랜덤 주문</span>
            <input
              type="number"
              value={randomCount}
              onChange={(e) => setRandomCount(Math.max(1, Number(e.target.value)))}
              min="1"
              max="100"
            />
            <span>개</span>
            <button type="button" onClick={handleRandom}>랜덤 전송</button>
          </div>
        </section>

        {/* 주문 목록 */}
        <div className="tables">
          {/* Producer */}
          <section className="card">
            <div className="section-header">
              <h2>Producer 주문 목록 (test1)</h2>
              <span className="total-count">총 {filteredProducer.length}건</span>
            </div>
            <div className="tab-bar">
              {STATUS_TABS.map(tab => (
                <button
                  key={tab}
                  className={`tab ${producerFilter === tab ? 'active' : ''}`}
                  onClick={() => handleFilterChange(tab)}
                >
                  {tab}
                </button>
              ))}
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr><th>ID</th><th>상품</th><th>수량</th><th>가격</th><th>상태</th><th>생성시간</th></tr>
                </thead>
                <tbody>
                  {pagedProducer.length > 0 ? pagedProducer.map((o) => (
                    <tr key={o.id}>
                      <td>{o.id}</td>
                      <td>{o.product}</td>
                      <td>{o.quantity}</td>
                      <td>{o.price?.toLocaleString()}</td>
                      <td className={`status ${o.status?.toLowerCase().replace('_', '-')}`}>{o.status}</td>
                      <td>{o.createdAt?.replace('T', ' ').slice(0, 19)}</td>
                    </tr>
                  )) : (
                    <tr><td colSpan="6" className="empty-state">데이터 없음</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pagination page={producerPage} total={totalProducerPages} onChange={setProducerPage} />
          </section>

          {/* Consumer */}
          <section className="card">
            <div className="section-header">
              <h2>Consumer 처리 목록 (test2)</h2>
              <span className="total-count">총 {consumerOrders.length}건</span>
            </div>
            <div className="table-scroll">
              <table>
                <thead>
                  <tr><th>ID</th><th>상품</th><th>수량</th><th>가격</th><th>상태</th><th>처리시간</th></tr>
                </thead>
                <tbody>
                  {pagedConsumer.length > 0 ? pagedConsumer.map((o) => (
                    <tr key={o.id}>
                      <td>{o.id}</td>
                      <td>{o.product}</td>
                      <td>{o.quantity}</td>
                      <td>{o.price?.toLocaleString()}</td>
                      <td className={`status ${o.status?.toLowerCase().replace('_', '-')}`}>{o.status}</td>
                      <td>{o.processedAt?.replace('T', ' ').slice(0, 19) ?? '-'}</td>
                    </tr>
                  )) : (
                    <tr><td colSpan="6" className="empty-state">데이터 없음</td></tr>
                  )}
                </tbody>
              </table>
            </div>
            <Pagination page={consumerPage} total={totalConsumerPages} onChange={setConsumerPage} />
          </section>
        </div>
      </div>

      {/* ── 오른쪽: Kafka CLI 실습 패널 ── */}
      <aside className="cmd-panel">
        <div className="cmd-panel-title">Kafka CLI 실습</div>
        <div className="cmd-panel-desc">VM에서 Kafka 설치 디렉토리 기준으로 실행</div>
        <div className="server-input-row">
          <label className="server-label">Bootstrap Server</label>
          <input
            className="server-input"
            value={bootstrapServer}
            onChange={(e) => setBootstrapServer(e.target.value)}
            placeholder="host:port"
            spellCheck={false}
          />
        </div>
        {kafkaCommands.map((cat) => (
          <div key={cat.category} className="cmd-category">
            <div className="cmd-category-title">{cat.category}</div>
            {cat.items.map((item) => (
              <div key={item.cmd} className="cmd-item">
                <div className="cmd-label">{item.label}</div>
                <div className="cmd-row">
                  <code className="cmd-code">{item.cmd}</code>
                  <button
                    className={`btn-copy ${copiedCmd === item.cmd ? 'copied' : ''}`}
                    onClick={() => handleCopy(item.cmd)}
                  >
                    {copiedCmd === item.cmd ? '복사됨' : '복사'}
                  </button>
                </div>
              </div>
            ))}
          </div>
        ))}
      </aside>

    </div>
  );
}

export default App;
