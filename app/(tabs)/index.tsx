
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  BackHandler,
  Easing,
  Platform,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import * as Application from 'expo-application';
import * as Haptics from 'expo-haptics';
import * as Linking from 'expo-linking';
import axios from 'axios';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { WebView, type WebViewMessageEvent } from 'react-native-webview';

type Tone = 'neutral' | 'success' | 'danger';
type SpeedPreset = '50' | '100' | '200' | 'custom';
type Banner = { message: string; tone: Tone };
type LogEntry = { id: string; message: string; tone: Tone; time: string };
type PriceStat = { count: number; success: number };
type BotMsg = { type: string; payload?: Record<string, unknown> };
type CheckPayload = Record<string, unknown> & {
  active?: unknown;
  subscription_uuid?: unknown;
  expiry_raw?: unknown;
  expiry?: unknown;
  expires_at?: unknown;
  expiry_at?: unknown;
  valid_till?: unknown;
  subscription_expiry?: unknown;
  remaining_seconds?: unknown;
  ttl_seconds?: unknown;
  expiry_seconds?: unknown;
  subscription?: Record<string, unknown>;
};

const BASE_URL = 'http://192.168.1.3:3000';
const APP_SCHEME = 'myapp';
const BUY_URL = 'https://arbpay.me/#/buy/arb';
const DISPLAY_PLAN_AMOUNT = 50;
const TEST_CHARGE_AMOUNT = 50;
const DEFAULT_PHONE = 'NULL';
const FIXED_MIN_PROFIT = 2;
const PLAN_FEATURES = [
  'Live arbpay buy-page automation',
  'High-speed scanner (50ms to 500ms)',
  'Smart adaptive filtering by win rate',
  'Safe mode with randomized human-like delays',
  'Device-based access, no login required',
];

const BOT_SCRIPT = `
(function () {
  if (window.__ARB_BOT__) {
    try { window.ReactNativeWebView.postMessage(JSON.stringify({ type: 'ready' })); } catch (e) {}
    return;
  }

  const state = {
    run: false,
    timer: null,
    lock: false,
    lastBuyTs: 0,
    lastFlipTs: 0,
    lastHeartbeatTs: 0,
    lastSkipTs: 0,
    learn: {},
    cfg: {
      minPrice: 100,
      maxPrice: 10000,
      minProfit: 2,
      speedMs: 120,
      smartMode: true,
      safeMode: false,
      cooldownMs: 700,
    },
  };

  const post = (type, payload) => {
    try {
      if (window.ReactNativeWebView) {
        window.ReactNativeWebView.postMessage(JSON.stringify({ type, payload: payload || {} }));
      }
    } catch (e) {}
  };

  const toNum = (value, fallback) => {
    const cleaned = String(value ?? '').replace(/[^0-9.]/g, '');
    const n = Number(cleaned.length ? cleaned : value);
    return Number.isFinite(n) ? n : fallback;
  };

  const clamp = (value, min, max) => Math.min(Math.max(value, min), max);
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const rand = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
  const bucketOf = (price) => Math.floor(price / 50) * 50;

  const isVisible = (el) =>
    !!el && !el.disabled && (typeof el.offsetParent === 'undefined' || el.offsetParent !== null);

  const normalize = (cfg) => {
    const out = Object.assign({}, state.cfg, cfg || {});
    out.minPrice = Math.max(0, toNum(out.minPrice, 100));
    out.maxPrice = Math.max(0, toNum(out.maxPrice, 10000));
    out.minProfit = 2;
    out.speedMs = clamp(toNum(out.speedMs, 120), 50, 500);
    out.cooldownMs = clamp(toNum(out.cooldownMs, 700), 300, 3000);
    out.smartMode = !!out.smartMode;
    out.safeMode = !!out.safeMode;
    return out;
  };

  const parseNumbers = (text) => {
    const out = [];
    const regex = /(?:\\u20B9|rs\\\\.?|inr)?\\\\s*([0-9]+(?:\\\\.[0-9]+)?)/gi;
    let match;
    while ((match = regex.exec(String(text || ''))) !== null) {
      const n = Number(match[1]);
      if (Number.isFinite(n) && n > 0) out.push(n);
    }
    return out;
  };

  const parseCard = (el) => {
    if (!el) return null;
    const text = String(el.innerText || el.textContent || '');
    const priceMatch = text.match(/\\u20B9\\s*([0-9]+(?:\\.[0-9]+)?)/i);
    const rewardMatch = text.match(/reward\\s*\\+?\\s*([0-9]+(?:\\.[0-9]+)?)/i);
    let price = priceMatch ? Number(priceMatch[1]) : NaN;
    let reward = rewardMatch ? Number(rewardMatch[1]) : NaN;

    if (!Number.isFinite(price) || price <= 0 || !Number.isFinite(reward) || reward <= 0) {
      const values = parseNumbers(text);
      if (values.length < 2) return null;
      if (!Number.isFinite(price) || price <= 0) {
        price = Math.max(...values);
      }
      if (!Number.isFinite(reward) || reward <= 0) {
        reward = Math.min(...values);
      }
    }
    if (!Number.isFinite(price) || !Number.isFinite(reward) || price <= 0 || reward <= 0) return null;
    return { price, reward, profitPct: (reward / price) * 100 };
  };

  const fireClick = (el) => {
    if (!el) return false;
    try {
      if (typeof el.scrollIntoView === 'function') {
        el.scrollIntoView({ behavior: 'auto', block: 'center', inline: 'center' });
      }
      const rect = el.getBoundingClientRect ? el.getBoundingClientRect() : null;
      const x = rect ? rect.left + rect.width / 2 + rand(-2, 2) : 8;
      const y = rect ? rect.top + rect.height / 2 + rand(-2, 2) : 8;
      ['pointerdown', 'touchstart', 'mouseover', 'mousedown', 'mouseup', 'touchend', 'pointerup', 'click'].forEach((eventName) => {
        try {
          if (eventName.startsWith('touch')) {
            el.dispatchEvent(new Event(eventName, { bubbles: true, cancelable: true }));
            return;
          }
          if (eventName.startsWith('pointer') && typeof PointerEvent === 'function') {
            el.dispatchEvent(new PointerEvent(eventName, { bubbles: true, cancelable: true, clientX: x, clientY: y }));
            return;
          }
          el.dispatchEvent(new MouseEvent(eventName, { view: window, bubbles: true, cancelable: true, clientX: x, clientY: y }));
        } catch (e) {}
      });
      if (typeof el.click === 'function') el.click();
      return true;
    } catch (e) {
      return false;
    }
  };

  const findTabs = () =>
    Array.from(document.querySelectorAll('button,[role="tab"],a,div,span')).filter((el) => {
      if (!isVisible(el)) return false;
      const t = String(el.innerText || el.textContent || '').trim().toLowerCase();
      return t === 'default' || t === 'large';
    });

  const maybeFlipTabs = () => {
    const now = Date.now();
    if (now - state.lastFlipTs < 220) return;
    const tabs = findTabs();
    if (!tabs.length) return;
    state.lastFlipTs = now;
    const target = Math.floor(now / 220) % 2 === 0 ? 'default' : 'large';
    for (let i = 0; i < tabs.length; i += 1) {
      const t = String(tabs[i].innerText || tabs[i].textContent || '').trim().toLowerCase();
      if (t === target) {
        fireClick(tabs[i]);
        return;
      }
    }
    fireClick(tabs[0]);
  };

  const isBuyLabel = (text) => {
    const t = String(text || '').trim().toLowerCase();
    return t === 'buy' || t.includes('buy now') || t.startsWith('buy');
  };

  const buyCountIn = (node) =>
    Array.from(node.querySelectorAll('button,[role="button"],a,div[role="button"]')).filter((el) =>
      isBuyLabel(el.innerText || el.textContent || ''),
    ).length;

  const findOrderCard = (btn) => {
    let node = btn;
    for (let depth = 0; depth < 7 && node; depth += 1) {
      const text = String(node.innerText || node.textContent || '').toLowerCase();
      if (text.includes('reward') && (text.includes('\u20B9') || text.includes('inr') || text.includes('rs')) && buyCountIn(node) === 1) {
        return node;
      }
      node = node.parentElement;
    }
    return btn.closest('article') || btn.closest('li') || btn.closest('section') || btn.closest('div') || btn;
  };

  const findBuyButtons = () =>
    Array.from(document.querySelectorAll('button,[role="button"],a,div[role="button"]')).filter((el) => {
      if (!isVisible(el)) return false;
      return isBuyLabel(el.innerText || el.textContent || '');
    });

  const closeEnough = (a, b) => Math.abs(a - b) <= 0.01;
  const sameOrder = (left, right) =>
    !!left && !!right && closeEnough(left.price, right.price) && closeEnough(left.reward, right.reward);

  const resolveTargetButton = (candidate, preferredBtn) => {
    const probe = (button) => {
      if (!button) return null;
      const card = findOrderCard(button);
      const parsed = parseCard(card || button);
      return sameOrder(candidate, parsed) ? button : null;
    };
    const direct = probe(preferredBtn);
    if (direct) return direct;
    const buttons = findBuyButtons();
    for (let i = 0; i < buttons.length; i += 1) {
      const hit = probe(buttons[i]);
      if (hit) return hit;
    }
    return null;
  };

  const rateOf = (price) => {
    const row = state.learn[bucketOf(price)];
    if (!row || !row.count) return null;
    return (row.success / row.count) * 100;
  };

  const updateLearn = (price, success) => {
    const bucket = bucketOf(price);
    if (!state.learn[bucket]) state.learn[bucket] = { count: 0, success: 0 };
    state.learn[bucket].count += 1;
    if (success) state.learn[bucket].success += 1;
  };

  const bestBucket = () => {
    const keys = Object.keys(state.learn);
    if (!keys.length) return null;
    let winBucket = null;
    let winRate = -1;
    let winCount = -1;
    for (let i = 0; i < keys.length; i += 1) {
      const row = state.learn[keys[i]];
      if (!row || !row.count) continue;
      const r = (row.success / row.count) * 100;
      if (r > winRate || (r === winRate && row.count > winCount)) {
        winBucket = Number(keys[i]);
        winRate = r;
        winCount = row.count;
      }
    }
    return winBucket;
  };

  const reportSkip = (reason, price) => {
    const now = Date.now();
    if (now - state.lastSkipTs < 1400) return;
    state.lastSkipTs = now;
    post('skipped', { reason, price });
  };

  const evaluate = async (candidate, btn) => {
    const cfg = state.cfg;
    if (candidate.price < cfg.minPrice || candidate.price > cfg.maxPrice) {
      return;
    }
    if (candidate.profitPct < cfg.minProfit) {
      return;
    }
    if (cfg.smartMode) {
      const rate = rateOf(candidate.price);
      if (rate !== null && rate < 30) {
        reportSkip('smart-low-success', candidate.price);
        return;
      }
    }
    if (state.lock) {
      reportSkip('buy-lock', candidate.price);
      return;
    }
    if (Date.now() - state.lastBuyTs < cfg.cooldownMs) {
      reportSkip('cooldown', candidate.price);
      return;
    }

    state.lock = true;
    try {
      state.lastFlipTs = Date.now() + 450;
      let targetBtn = resolveTargetButton(candidate, btn);
      if (!targetBtn) {
        reportSkip('order-shifted', candidate.price);
        return;
      }
      if (cfg.safeMode) await sleep(rand(50, 300));
      await sleep(rand(10, 24));

      targetBtn = resolveTargetButton(candidate, targetBtn) || resolveTargetButton(candidate, btn);
      if (!targetBtn) {
        reportSkip('order-shifted', candidate.price);
        return;
      }

      let ok = fireClick(targetBtn);
      if (!ok && targetBtn && typeof targetBtn.querySelector === 'function') {
        const inner = targetBtn.querySelector('button,[role="button"],a,div[role="button"]');
        if (inner) ok = fireClick(inner);
      }
      state.lastBuyTs = Date.now();
      updateLearn(candidate.price, ok);
      post(ok ? 'bought' : 'buyFailed', {
        price: candidate.price,
        reward: candidate.reward,
        profitPct: candidate.profitPct,
        bestBucket: bestBucket(),
      });
    } catch (err) {
      updateLearn(candidate.price, false);
      post('buyFailed', { price: candidate.price, error: String(err) });
    } finally {
      state.lock = false;
    }
  };

  const tick = async () => {
    if (!state.run) return;
    let scanned = 0;
    let eligible = 0;
    try {
      maybeFlipTabs();
      const buttons = findBuyButtons();
      for (let i = 0; i < buttons.length; i += 1) {
        if (!state.run) break;
        const btn = buttons[i];
        const card = findOrderCard(btn);
        const candidate = parseCard(card || btn);
        if (!candidate) continue;
        scanned += 1;
        const inRange = candidate.price >= state.cfg.minPrice && candidate.price <= state.cfg.maxPrice;
        const profitable = candidate.profitPct >= state.cfg.minProfit;
        if (!inRange || !profitable) continue;
        eligible += 1;
        post('detected', { price: candidate.price, reward: candidate.reward, profitPct: candidate.profitPct });
        await evaluate(candidate, btn);
        if (scanned >= 8) break;
      }
      const now = Date.now();
      if (now - state.lastHeartbeatTs > 750) {
        state.lastHeartbeatTs = now;
        post('heartbeat', { bestBucket: bestBucket(), scanned, eligible });
      }
    } catch (err) {
      post('engineError', { message: String(err && err.message ? err.message : err) });
    } finally {
      if (state.run) state.timer = setTimeout(tick, state.cfg.speedMs);
    }
  };

  window.__ARB_BOT__ = {
    start: (cfg) => {
      state.cfg = normalize(cfg || {});
      if (state.run) {
        post('running', { running: true });
        return;
      }
      state.run = true;
      post('running', { running: true, cfg: state.cfg });
      tick();
    },
    stop: () => {
      state.run = false;
      if (state.timer) {
        clearTimeout(state.timer);
        state.timer = null;
      }
      state.lock = false;
      post('running', { running: false });
    },
    updateConfig: (cfg) => {
      state.cfg = normalize(cfg || {});
      post('config', { cfg: state.cfg });
    },
    ping: () => post('ready', { href: window.location.href }),
  };

  post('ready', { href: window.location.href });
})();
true;`;

const fmtTime = () => new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
const clamp = (v: number, min: number, max: number) => Math.min(Math.max(v, min), max);
const num = (v: string, d: number) => {
  const cleaned = String(v ?? '').replace(/[^0-9.]/g, '');
  const n = Number(cleaned.length ? cleaned : v);
  return Number.isFinite(n) ? n : d;
};
const onlyDigits = (v: string) => String(v ?? '').replace(/[^0-9]/g, '');
const pad2 = (n: number) => String(n).padStart(2, '0');
const parseSqlDateToEpoch = (text: string): number | null => {
  const m = text.match(
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(?:\s*(Z|[+-]\d{2}:?\d{2}|[+-]\d{2}))?$/,
  );
  if (!m) return null;

  const year = Number(m[1]);
  const month = Number(m[2]) - 1;
  const day = Number(m[3]);
  const hour = Number(m[4]);
  const minute = Number(m[5]);
  const second = Number(m[6]);
  const msRaw = (m[7] ?? '').padEnd(3, '0').slice(0, 3);
  const millis = Number(msRaw || 0);
  const tz = m[8];

  if (!tz) {
    const local = new Date(year, month, day, hour, minute, second, millis).getTime();
    return Number.isFinite(local) ? local : null;
  }

  if (tz.toUpperCase() === 'Z') {
    return Date.UTC(year, month, day, hour, minute, second, millis);
  }

  const sign = tz.startsWith('-') ? -1 : 1;
  const body = tz.slice(1).replace(':', '');
  const tzHour = Number(body.slice(0, 2) || '0');
  const tzMinute = Number(body.slice(2, 4) || '0');
  const offsetMs = (tzHour * 60 + tzMinute) * 60 * 1000;
  return Date.UTC(year, month, day, hour, minute, second, millis) - sign * offsetMs;
};
const toEpochMs = (value: unknown): number | null => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    if (value > 1e12) return value;
    if (value > 1e9) return value * 1000;
    return null;
  }
  if (typeof value === 'string') {
    const text = value.trim();
    if (!text) return null;
    const asNum = Number(text);
    if (Number.isFinite(asNum)) return toEpochMs(asNum);
    const normalized = text.includes('T') ? text : text.replace(' ', 'T');
    const parsed = Date.parse(normalized);
    if (Number.isFinite(parsed)) return parsed;
    const strict = parseSqlDateToEpoch(text);
    if (strict !== null) return strict;
  }
  return null;
};
const parseExpiryMs = (payload: CheckPayload): number | null => {
  const sub = (payload.subscription ?? {}) as Record<string, unknown>;
  const direct = [
    payload.expiry_raw,
    payload.expiry,
    payload.expires_at,
    payload.expiry_at,
    payload.valid_till,
    payload.subscription_expiry,
    sub.expiry,
    sub.expires_at,
    sub.expiry_at,
    sub.valid_till,
  ];
  for (let i = 0; i < direct.length; i += 1) {
    const ms = toEpochMs(direct[i]);
    if (ms !== null) return ms;
  }
  const ttl = [payload.remaining_seconds, payload.ttl_seconds, payload.expiry_seconds, sub.remaining_seconds, sub.ttl_seconds];
  for (let i = 0; i < ttl.length; i += 1) {
    const secs = Number(ttl[i]);
    if (Number.isFinite(secs) && secs >= 0) return Date.now() + Math.max(0, secs) * 1000;
  }
  return null;
};
const formatRemaining = (ms: number | null) => {
  if (ms === null) return 'Unknown';
  if (ms <= 0) return 'Expired';
  const total = Math.floor(ms / 1000);
  const d = Math.floor(total / 86400);
  const h = Math.floor((total % 86400) / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  if (d > 0) return `${d}d ${pad2(h)}:${pad2(m)}:${pad2(s)}`;
  return `${pad2(h)}:${pad2(m)}:${pad2(s)}`;
};

export default function Index() {
  const insets = useSafeAreaInsets();
  const webRef = useRef<WebView>(null);
  const deviceRef = useRef('');
  const learningRef = useRef<Record<number, PriceStat>>({});
  const soundRef = useRef(false);
  const bannerTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastBackPressRef = useRef(0);
  const expirySyncRef = useRef(false);
  const expiryMsRef = useRef<number | null>(null);
  const subscriptionUuidRef = useRef('');

  const [deviceId, setDeviceId] = useState('');
  const [subscriptionUuid, setSubscriptionUuid] = useState('');
  const [subscriptionExpiryMs, setSubscriptionExpiryMs] = useState<number | null>(null);
  const [nowMs, setNowMs] = useState(Date.now());
  const [active, setActive] = useState(false);
  const [checking, setChecking] = useState(true);
  const [checkedOnce, setCheckedOnce] = useState(false);
  const [webReady, setWebReady] = useState(false);
  const [webCanGoBack, setWebCanGoBack] = useState(false);
  const [webUrl, setWebUrl] = useState(BUY_URL);
  const [running, setRunning] = useState(false);
  const [logsOpen, setLogsOpen] = useState(false);
  const [headerMinimized, setHeaderMinimized] = useState(false);

  const [minPrice, setMinPrice] = useState('100');
  const [maxPrice, setMaxPrice] = useState('10000');
  const [speedPreset, setSpeedPreset] = useState<SpeedPreset>('200');
  const [customSpeed, setCustomSpeed] = useState('200');
  const [smart, setSmart] = useState(true);
  const [safe, setSafe] = useState(false);
  const [sound, setSound] = useState(false);

  const [stats, setStats] = useState({ ordersDetected: 0, buyAttempts: 0, ordersBought: 0, estimatedProfit: 0 });
  const [bestRange, setBestRange] = useState('N/A');
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [banner, setBanner] = useState<Banner | null>(null);
  const minPriceRef = useRef('100');
  const maxPriceRef = useRef('10000');

  const bannerOpacity = useRef(new Animated.Value(0)).current;
  const bannerY = useRef(new Animated.Value(-10)).current;

  useEffect(() => {
    soundRef.current = sound;
  }, [sound]);

  useEffect(() => {
    deviceRef.current = deviceId;
  }, [deviceId]);

  useEffect(() => {
    expiryMsRef.current = subscriptionExpiryMs;
  }, [subscriptionExpiryMs]);

  useEffect(() => {
    subscriptionUuidRef.current = subscriptionUuid;
  }, [subscriptionUuid]);

  useEffect(() => {
    minPriceRef.current = minPrice;
  }, [minPrice]);

  useEffect(() => {
    maxPriceRef.current = maxPrice;
  }, [maxPrice]);

  const successRate = useMemo(() => (stats.buyAttempts ? (stats.ordersBought / stats.buyAttempts) * 100 : 0), [stats.buyAttempts, stats.ordersBought]);
  const remainingMs = useMemo(
    () => (subscriptionExpiryMs === null ? null : Math.max(subscriptionExpiryMs - nowMs, 0)),
    [nowMs, subscriptionExpiryMs],
  );
  const expiryText = useMemo(() => formatRemaining(remainingMs), [remainingMs]);
  const speedMs = useMemo(() => (speedPreset === 'custom' ? clamp(num(customSpeed, 200), 50, 500) : Number(speedPreset)), [customSpeed, speedPreset]);
  const cfg = useMemo(() => ({
    minPrice: Math.max(0, num(minPrice, 100)),
    maxPrice: Math.max(0, num(maxPrice, 10000)),
    minProfit: FIXED_MIN_PROFIT,
    speedMs,
    smartMode: smart,
    safeMode: safe,
    cooldownMs: safe ? 1100 : 700,
  }), [maxPrice, minPrice, safe, smart, speedMs]);
  const getLiveCfg = useCallback(() => {
    const liveMin = Math.max(0, num(minPriceRef.current, 100));
    const liveMax = Math.max(0, num(maxPriceRef.current, 10000));
    return { ...cfg, minPrice: liveMin, maxPrice: liveMax };
  }, [cfg]);

  const showBanner = useCallback((message: string, tone: Tone) => {
    setBanner({ message, tone });
    if (bannerTimer.current) clearTimeout(bannerTimer.current);
    Animated.parallel([
      Animated.timing(bannerOpacity, { toValue: 1, duration: 170, easing: Easing.out(Easing.quad), useNativeDriver: true }),
      Animated.timing(bannerY, { toValue: 0, duration: 170, easing: Easing.out(Easing.quad), useNativeDriver: true }),
    ]).start();
    bannerTimer.current = setTimeout(() => {
      Animated.parallel([
        Animated.timing(bannerOpacity, { toValue: 0, duration: 160, easing: Easing.in(Easing.quad), useNativeDriver: true }),
        Animated.timing(bannerY, { toValue: -10, duration: 160, easing: Easing.in(Easing.quad), useNativeDriver: true }),
      ]).start(({ finished }) => { if (finished) setBanner(null); });
    }, 1700);
  }, [bannerOpacity, bannerY]);

  const notify = useCallback(async (message: string, tone: Tone = 'neutral') => {
    showBanner(message, tone);
    if (!soundRef.current) return;
    try {
      if (tone === 'success') await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
      else if (tone === 'danger') await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
      else await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
    } catch {}
  }, [showBanner]);

  const pushLog = useCallback((message: string, tone: Tone = 'neutral') => {
    setLogs((prev) => [{ id: `${Date.now()}-${Math.random()}`, message, tone, time: fmtTime() }, ...prev].slice(0, 80));
  }, []);

  const resolveDeviceId = useCallback(async () => {
    if (Platform.OS === 'android') {
      const id = Application.getAndroidId();
      if (id) return id;
    }
    if (Platform.OS === 'ios') {
      const id = await Application.getIosIdForVendorAsync();
      if (id) return id;
    }
    return `device-${Application.applicationId ?? 'arb'}-${Date.now()}`;
  }, []);

  const checkSubscription = useCallback(async (source: 'launch' | 'manual' | 'deeplink' | 'expiry' = 'launch') => {
    setChecking(true);
    try {
      const id = deviceRef.current || (await resolveDeviceId());
      deviceRef.current = id;
      setDeviceId(id);
      const res = await axios.get(`${BASE_URL}/check`, { params: { device_id: id }, timeout: 6000 });
      const payload = (res.data ?? {}) as CheckPayload;
      const isActive = Boolean(payload.active);
      const nextSubscriptionUuid = typeof payload.subscription_uuid === 'string' ? payload.subscription_uuid : '';
      let expiryMs = parseExpiryMs(payload);
      if (expiryMs === null && nextSubscriptionUuid && nextSubscriptionUuid === subscriptionUuidRef.current) {
        expiryMs = expiryMsRef.current;
      }
      setActive(isActive);
      setSubscriptionUuid(nextSubscriptionUuid);
      setSubscriptionExpiryMs(expiryMs);
      setNowMs(Date.now());
      if (source === 'expiry') {
        await notify(isActive ? 'Subscription active. Bot unlocked.' : 'Subscription expired. Please renew.', isActive ? 'success' : 'danger');
      } else if (source !== 'launch') {
        await notify(isActive ? 'Subscription active. Bot unlocked.' : 'Subscription still inactive.', isActive ? 'success' : 'danger');
      }
    } catch {
      setActive(false);
      setSubscriptionExpiryMs(null);
      if (source === 'expiry') await notify('Could not verify expiry right now.', 'danger');
      else if (source !== 'launch') await notify('Unable to verify subscription right now.', 'danger');
    } finally {
      setCheckedOnce(true);
      setChecking(false);
    }
  }, [notify, resolveDeviceId]);

  const sendCmd = useCallback((command: 'start' | 'stop' | 'updateConfig' | 'ping', payload?: unknown) => {
    const js = payload === undefined
      ? `(function(){if(window.__ARB_BOT__){window.__ARB_BOT__.${command}();}})();true;`
      : `(function(){if(window.__ARB_BOT__){window.__ARB_BOT__.${command}(${JSON.stringify(payload)});}})();true;`;
    webRef.current?.injectJavaScript(js);
  }, []);

  const injectBot = useCallback(() => {
    webRef.current?.injectJavaScript(BOT_SCRIPT);
  }, []);

  const updateLearning = useCallback((price: number, success: boolean) => {
    const b = Math.floor(price / 50) * 50;
    const rec = learningRef.current[b] ?? { count: 0, success: 0 };
    rec.count += 1;
    if (success) rec.success += 1;
    learningRef.current[b] = rec;

    let best: number | null = null;
    let bestRate = -1;
    let bestCount = -1;
    for (const [k, v] of Object.entries(learningRef.current)) {
      if (!v.count) continue;
      const r = (v.success / v.count) * 100;
      if (r > bestRate || (r === bestRate && v.count > bestCount)) {
        best = Number(k);
        bestRate = r;
        bestCount = v.count;
      }
    }
    if (best === null) setBestRange('N/A');
    else setBestRange(`\u20B9${best} - \u20B9${best + 49} (${Math.round(bestRate)}%)`);
  }, []);

  const onWebMessage = useCallback((event: WebViewMessageEvent) => {
    let msg: BotMsg;
    try {
      msg = JSON.parse(event.nativeEvent.data) as BotMsg;
    } catch {
      return;
    }
    const p = msg.payload ?? {};
    switch (msg.type) {
      case 'ready':
        setWebReady(true);
        break;
      case 'running':
        setRunning(Boolean(p.running));
        break;
      case 'bought': {
        const price = Number(p.price);
        const reward = Number(p.reward);
        const ok = Number.isFinite(price) && Number.isFinite(reward);
        setStats((prev) => ({ ...prev, buyAttempts: prev.buyAttempts + 1, ordersBought: prev.ordersBought + 1, estimatedProfit: prev.estimatedProfit + (ok ? Math.max(reward - price, 0) : 0) }));
        if (ok) {
          updateLearning(price, true);
          pushLog(`Bought \u2705 (\u20B9${price})`, 'success');
        } else pushLog('Bought \u2705', 'success');
        void notify('Buy success', 'success');
        break;
      }
      case 'buyFailed': {
        const price = Number(p.price);
        setStats((prev) => ({ ...prev, buyAttempts: prev.buyAttempts + 1 }));
        if (Number.isFinite(price)) {
          updateLearning(price, false);
          pushLog(`Buy failed (\u20B9${price})`, 'danger');
        } else pushLog('Buy failed', 'danger');
        void notify('Buy failed', 'danger');
        break;
      }
      case 'skipped': {
        const reason = typeof p.reason === 'string' ? p.reason : 'filtered';
        const price = Number(p.price);
        if (Number.isFinite(price)) pushLog(`Skipped (${reason}) \u20B9${price}`, 'neutral');
        else pushLog(`Skipped (${reason})`, 'neutral');
        break;
      }
      case 'heartbeat': {
        const b = Number(p.bestBucket);
        const eligible = Number(p.eligible);
        if (Number.isFinite(eligible) && eligible > 0) {
          setStats((prev) => ({ ...prev, ordersDetected: prev.ordersDetected + Math.floor(eligible) }));
        }
        if (Number.isFinite(b)) setBestRange(`\u20B9${b} - \u20B9${b + 49}`);
        break;
      }
      case 'engineError': {
        const text = typeof p.message === 'string' ? p.message : 'Unknown engine error';
        pushLog(`Engine error: ${text}`, 'danger');
        void notify('Automation engine error', 'danger');
        break;
      }
      default:
        break;
    }
  }, [notify, pushLog, updateLearning]);

  const startBot = useCallback(() => {
    const liveCfg = getLiveCfg();
    if (!active) {
      Alert.alert('Subscription Required', 'Activate your plan before starting the bot.');
      return;
    }
    if (liveCfg.maxPrice < liveCfg.minPrice) {
      Alert.alert('Invalid Range', 'Max Price must be greater than or equal to Min Price.');
      return;
    }
    if (!webReady) {
      injectBot();
      pushLog('Preparing website automation engine...', 'neutral');
    }
    sendCmd('start', liveCfg);
    setRunning(true);
    pushLog(`Bot started (${liveCfg.speedMs}ms) range \u20B9${liveCfg.minPrice}-\u20B9${liveCfg.maxPrice}`, 'success');
    void notify('Bot started', 'success');
  }, [active, getLiveCfg, injectBot, notify, pushLog, sendCmd, webReady]);

  const stopBot = useCallback(() => {
    sendCmd('stop');
    setRunning(false);
    pushLog('Bot stopped', 'danger');
    void notify('Bot stopped', 'danger');
  }, [notify, pushLog, sendCmd]);

  const normalizeSpeed = useCallback(() => {
    if (speedPreset !== 'custom') return;
    setCustomSpeed(String(clamp(num(customSpeed, 200), 50, 500)));
  }, [customSpeed, speedPreset]);

  const onMinPriceChange = useCallback((v: string) => {
    const next = onlyDigits(v);
    minPriceRef.current = next;
    setMinPrice(next);
  }, []);

  const onMaxPriceChange = useCallback((v: string) => {
    const next = onlyDigits(v);
    maxPriceRef.current = next;
    setMaxPrice(next);
  }, []);

  const onCustomSpeedChange = useCallback((v: string) => {
    setCustomSpeed(onlyDigits(v));
  }, []);

  const toggleHeader = useCallback(() => {
    setHeaderMinimized((prev) => {
      const next = !prev;
      if (next) setLogsOpen(false);
      return next;
    });
  }, []);

  const buyNow = useCallback(async () => {
    const id = deviceRef.current || (await resolveDeviceId());
    if (!deviceRef.current) {
      deviceRef.current = id;
      setDeviceId(id);
    }
    try {
      const res = await axios.post(`${BASE_URL}/payment/init`, { device_id: id, amount: TEST_CHARGE_AMOUNT, phone: DEFAULT_PHONE }, { timeout: 7000 });
      const url = res.data?.payment_url;
      if (!url || typeof url !== 'string') throw new Error('Missing payment URL');
      if (typeof res.data?.subscription_uuid === 'string') setSubscriptionUuid(res.data.subscription_uuid);
      await Linking.openURL(url);
    } catch {
      Alert.alert('Payment Error', 'Unable to start payment right now.');
    }
  }, [resolveDeviceId]);

  const handleDeepLink = useCallback(async (url: string) => {
    const parsed = Linking.parse(url);
    const ok = url.toLowerCase().startsWith(`${APP_SCHEME}://payment-success`) || parsed.path === 'payment-success';
    if (!ok) return;
    pushLog('Payment success callback received', 'success');
    await notify('Payment success received. Verifying...', 'neutral');
    await checkSubscription('deeplink');
  }, [checkSubscription, notify, pushLog]);

  useEffect(() => {
    void checkSubscription('launch');
  }, [checkSubscription]);

  useEffect(() => {
    if (subscriptionExpiryMs === null) return;
    const timer = setInterval(() => setNowMs(Date.now()), 1000);
    return () => clearInterval(timer);
  }, [subscriptionExpiryMs]);

  useEffect(() => {
    if (remainingMs === null || remainingMs > 0) {
      expirySyncRef.current = false;
      return;
    }
    if (expirySyncRef.current) return;
    expirySyncRef.current = true;
    pushLog('Subscription timer ended. Rechecking...', 'neutral');
    void checkSubscription('expiry');
  }, [checkSubscription, pushLog, remainingMs]);

  useEffect(() => {
    void Linking.getInitialURL().then((url) => { if (url) void handleDeepLink(url); });
    const sub = Linking.addEventListener('url', ({ url }) => void handleDeepLink(url));
    return () => sub.remove();
  }, [handleDeepLink]);

  useEffect(() => {
    if (!active) return;
    injectBot();
    sendCmd('ping');
  }, [active, injectBot, sendCmd]);

  useEffect(() => {
    if (!running) return;
    sendCmd('updateConfig', getLiveCfg());
  }, [getLiveCfg, running, sendCmd]);

  useEffect(() => {
    if (Platform.OS !== 'android') return;
    const sub = BackHandler.addEventListener('hardwareBackPress', () => {
      if (!active) return false;
      const onBuyRoute = (webUrl || '').includes('#/buy/arb');
      if (webCanGoBack && !onBuyRoute) {
        webRef.current?.goBack();
        return true;
      }
      const now = Date.now();
      if (now - lastBackPressRef.current < 1600) {
        BackHandler.exitApp();
        return true;
      }
      lastBackPressRef.current = now;
      void notify('Press back again to exit', 'neutral');
      return true;
    });
    return () => sub.remove();
  }, [active, notify, webCanGoBack, webUrl]);

  useEffect(() => () => { if (bannerTimer.current) clearTimeout(bannerTimer.current); }, []);

  if (!checkedOnce && checking) {
    return (
      <View style={styles.center}>
        <ActivityIndicator size="large" color="#00ff99" />
        <Text style={styles.loading}>Checking subscription...</Text>
      </View>
    );
  }

  const status = running ? 'Running \u2705' : 'Stopped \u274C';
  const statusColor = running ? '#00ff99' : '#ff5d61';

  return (
    <View style={styles.screen}>
      {banner ? (
        <Animated.View style={[styles.banner, banner.tone === 'success' ? styles.bannerSuccess : banner.tone === 'danger' ? styles.bannerDanger : styles.bannerNeutral, { opacity: bannerOpacity, transform: [{ translateY: bannerY }] }]}>
          <Text style={styles.bannerText}>{banner.message}</Text>
        </Animated.View>
      ) : null}

      {!active ? (
        <View style={styles.planWrap}>
          <View style={styles.planCard}>
            <Text style={styles.planTitle}>Subscription Plan</Text>
            <Text style={styles.planPrice}>{'\u20B9'}{DISPLAY_PLAN_AMOUNT}</Text>
            <Text style={styles.planValidity}>Valid for 5 Days</Text>
            <View style={styles.planList}>
              {PLAN_FEATURES.map((f) => (
                <View key={f} style={styles.planItem}>
                  <Text style={styles.planDot}>{'\u2022'}</Text>
                  <Text style={styles.planText}>{f}</Text>
                </View>
              ))}
            </View>
            <TouchableOpacity style={styles.buyBtn} onPress={() => void buyNow()}>
              <Text style={styles.buyBtnText}>Buy Now</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.recheckBtn} onPress={() => void checkSubscription('manual')} disabled={checking}>
              <Text style={styles.recheckText}>{checking ? 'Checking...' : 'I Paid, Recheck'}</Text>
            </TouchableOpacity>
            <Text style={styles.meta}>Displayed: {'\u20B9'}{DISPLAY_PLAN_AMOUNT} | Charge: {'\u20B9'}{TEST_CHARGE_AMOUNT}</Text>
            <Text style={styles.meta}>Device ID: {deviceId || 'loading...'}</Text>
            <Text style={styles.meta}>Subscription UUID: {subscriptionUuid || 'creating...'}</Text>
          </View>
        </View>
      ) : (
        <View style={styles.botRoot}>
          <View style={[styles.header, headerMinimized && styles.headerMinimized, { paddingTop: insets.top + 8 }]}>
            <View style={styles.topRow}>
              <Text style={styles.title}>ARB Smart Bot</Text>
              <View style={styles.topRowRight}>
                <View style={[styles.statusPill, { borderColor: statusColor }]}>
                  <Text style={[styles.statusPillText, { color: statusColor }]}>{status}</Text>
                </View>
                <TouchableOpacity style={styles.minimizeBtn} onPress={toggleHeader}>
                  <Text style={styles.minimizeBtnText}>{headerMinimized ? 'EXPAND' : 'MIN'}</Text>
                </TouchableOpacity>
              </View>
            </View>
            <View style={styles.expiryRow}>
              <Text style={styles.expiryLabel}>Plan Left</Text>
              <Text style={[styles.expiryValue, remainingMs !== null && remainingMs <= 0 ? styles.bad : styles.ok]}>
                {expiryText}
              </Text>
            </View>

            {headerMinimized ? (
              <View style={styles.miniRow}>
                <TouchableOpacity style={[styles.ctrlBtn, running ? styles.stop : styles.start]} onPress={running ? stopBot : startBot}>
                  <Text style={styles.ctrlText}>{running ? 'STOP' : 'START'}</Text>
                </TouchableOpacity>
              </View>
            ) : (
              <>
                <View style={styles.inputsRow}>
                  <View style={styles.field}><Text style={styles.fieldLabel}>Min</Text><TextInput style={styles.fieldInput} value={minPrice} onChangeText={onMinPriceChange} keyboardType="number-pad" /></View>
                  <View style={styles.field}><Text style={styles.fieldLabel}>Max</Text><TextInput style={styles.fieldInput} value={maxPrice} onChangeText={onMaxPriceChange} keyboardType="number-pad" /></View>
                </View>
                <Text style={styles.fixedProfitNote}>Auto profit filter: {FIXED_MIN_PROFIT}%+</Text>

                <View style={styles.speedRow}>
                  {(['50', '100', '200', 'custom'] as SpeedPreset[]).map((p) => (
                    <TouchableOpacity key={p} style={[styles.speedChip, speedPreset === p && styles.speedChipActive]} onPress={() => setSpeedPreset(p)}>
                      <Text style={[styles.speedText, speedPreset === p && styles.speedTextActive]}>{p === 'custom' ? 'Custom' : `${p}ms`}</Text>
                    </TouchableOpacity>
                  ))}
                  {speedPreset === 'custom' ? <TextInput style={styles.customInput} value={customSpeed} onChangeText={onCustomSpeedChange} onBlur={normalizeSpeed} keyboardType="number-pad" /> : null}
                </View>

                <View style={styles.switchRow}>
                  <View style={styles.switchCell}><Text style={styles.switchLabel}>Smart</Text><Switch value={smart} onValueChange={setSmart} trackColor={{ false: '#3a3a3a', true: '#0bbf75' }} thumbColor={smart ? '#00ff99' : '#b5b5b5'} /></View>
                  <View style={styles.switchCell}><Text style={styles.switchLabel}>Safe</Text><Switch value={safe} onValueChange={setSafe} trackColor={{ false: '#3a3a3a', true: '#0bbf75' }} thumbColor={safe ? '#00ff99' : '#b5b5b5'} /></View>
                  <View style={styles.switchCell}><Text style={styles.switchLabel}>Sound</Text><Switch value={sound} onValueChange={setSound} trackColor={{ false: '#3a3a3a', true: '#0bbf75' }} thumbColor={sound ? '#00ff99' : '#b5b5b5'} /></View>
                </View>

                <View style={styles.btnRow}>
                  <TouchableOpacity style={[styles.ctrlBtn, styles.start]} onPress={startBot}><Text style={styles.ctrlText}>START</Text></TouchableOpacity>
                  <TouchableOpacity style={[styles.ctrlBtn, styles.stop]} onPress={stopBot}><Text style={styles.ctrlText}>STOP</Text></TouchableOpacity>
                  <TouchableOpacity style={[styles.ctrlBtn, styles.logs]} onPress={() => setLogsOpen((v) => !v)}><Text style={styles.ctrlText}>{logsOpen ? 'HIDE' : 'LOGS'}</Text></TouchableOpacity>
                </View>

                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.statsScroll}>
                  <View style={styles.stat}><Text style={styles.statL}>Detected</Text><Text style={styles.statV}>{stats.ordersDetected}</Text></View>
                  <View style={styles.stat}><Text style={styles.statL}>Bought</Text><Text style={styles.statV}>{stats.ordersBought}</Text></View>
                  <View style={styles.stat}><Text style={styles.statL}>Success</Text><Text style={styles.statV}>{successRate.toFixed(1)}%</Text></View>
                  <View style={styles.stat}><Text style={styles.statL}>Profit</Text><Text style={styles.statV}>{'\u20B9'}{stats.estimatedProfit}</Text></View>
                  <View style={styles.statWide}><Text style={styles.statL}>Best Range</Text><Text style={styles.statV}>{bestRange}</Text></View>
                </ScrollView>

                {logsOpen ? (
                  <View style={styles.logPanel}>
                    <ScrollView showsVerticalScrollIndicator={false}>
                      {logs.length === 0 ? <Text style={styles.logEmpty}>No activity yet.</Text> : logs.slice(0, 30).map((l) => (
                        <View key={l.id} style={styles.logRow}>
                          <Text style={[styles.logMsg, l.tone === 'success' ? styles.ok : l.tone === 'danger' ? styles.bad : styles.neutral]}>{l.message}</Text>
                          <Text style={styles.logT}>{l.time}</Text>
                        </View>
                      ))}
                    </ScrollView>
                  </View>
                ) : null}
              </>
            )}
          </View>

          <View style={styles.webWrap}>
            <WebView
              ref={webRef}
              source={{ uri: BUY_URL }}
              style={styles.web}
              javaScriptEnabled
              domStorageEnabled
              mixedContentMode="always"
              androidLayerType="hardware"
              setSupportMultipleWindows={false}
              cacheEnabled
              scrollEnabled
              nestedScrollEnabled
              bounces
              overScrollMode="content"
              showsVerticalScrollIndicator
              showsHorizontalScrollIndicator={false}
              startInLoadingState
              injectedJavaScriptBeforeContentLoaded={BOT_SCRIPT}
              onMessage={onWebMessage}
              onNavigationStateChange={(navState) => {
                setWebCanGoBack(Boolean(navState.canGoBack));
                if (typeof navState.url === 'string' && navState.url) setWebUrl(navState.url);
              }}
              onLoadEnd={() => {
                setWebReady(false);
                injectBot();
                sendCmd('ping');
              }}
              onError={(e) => {
                pushLog(`WebView error: ${e.nativeEvent.description}`, 'danger');
                void notify('Website loading error', 'danger');
              }}
            />
          </View>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1, backgroundColor: '#000' },
  center: { flex: 1, backgroundColor: '#000', alignItems: 'center', justifyContent: 'center' },
  loading: { marginTop: 10, color: '#b5c2bb', fontSize: 15 },
  banner: { position: 'absolute', zIndex: 20, top: 12, left: 12, right: 12, borderRadius: 12, paddingVertical: 10, paddingHorizontal: 12, borderWidth: 1 },
  bannerSuccess: { backgroundColor: '#052014', borderColor: '#00ff99' },
  bannerDanger: { backgroundColor: '#2a0d10', borderColor: '#ff4d4f' },
  bannerNeutral: { backgroundColor: '#102218', borderColor: '#2f5f48' },
  bannerText: { color: '#ecfff4', textAlign: 'center', fontSize: 13, fontWeight: '600' },
  planWrap: { flex: 1, justifyContent: 'center', alignItems: 'center', padding: 20 },
  planCard: { width: '100%', maxWidth: 440, backgroundColor: '#0f1110', borderRadius: 16, padding: 18, borderWidth: 1, borderColor: '#2a4c3a', alignItems: 'center' },
  planTitle: { color: '#f0fff5', fontSize: 25, fontWeight: '800' },
  planPrice: { color: '#00ff99', fontSize: 40, fontWeight: '800', marginTop: 4 },
  planValidity: { color: '#b0c7bc', fontSize: 14, marginBottom: 12 },
  planList: { width: '100%', borderWidth: 1, borderColor: '#20382c', borderRadius: 12, backgroundColor: '#0b1210', padding: 10, marginBottom: 14, gap: 6 },
  planItem: { flexDirection: 'row', alignItems: 'flex-start', gap: 7 },
  planDot: { color: '#00ff99', marginTop: 1 },
  planText: { color: '#cbdfd6', fontSize: 12, flex: 1 },
  buyBtn: { width: '100%', backgroundColor: '#00ff99', borderRadius: 11, paddingVertical: 12, alignItems: 'center', marginBottom: 8 },
  buyBtnText: { color: '#05120d', fontWeight: '800', fontSize: 15 },
  recheckBtn: { width: '100%', borderRadius: 11, borderWidth: 1, borderColor: '#2e5a45', backgroundColor: '#121f18', paddingVertical: 10, alignItems: 'center', marginBottom: 10 },
  recheckText: { color: '#cceadd', fontWeight: '700', fontSize: 13 },
  meta: { color: '#8ea599', fontSize: 11, textAlign: 'center' },
  botRoot: { flex: 1 },
  header: { backgroundColor: '#0c1210', borderBottomWidth: 1, borderBottomColor: '#1f3f31', paddingHorizontal: 10, paddingBottom: 10, gap: 7 },
  headerMinimized: { paddingBottom: 8, gap: 5 },
  topRow: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  topRowRight: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  title: { color: '#f1fff7', fontSize: 21, fontWeight: '800' },
  expiryRow: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', borderWidth: 1, borderColor: '#1f3d2f', borderRadius: 10, backgroundColor: '#101915', paddingHorizontal: 10, paddingVertical: 7 },
  expiryLabel: { color: '#95ab9f', fontSize: 11, fontWeight: '600' },
  expiryValue: { fontSize: 12, fontWeight: '800' },
  statusPill: { borderWidth: 1, borderRadius: 999, paddingHorizontal: 10, paddingVertical: 4, backgroundColor: '#111b16' },
  statusPillText: { fontSize: 12, fontWeight: '700' },
  minimizeBtn: { borderWidth: 1, borderColor: '#2f5b47', borderRadius: 999, backgroundColor: '#14201a', paddingHorizontal: 10, paddingVertical: 4 },
  minimizeBtnText: { color: '#9fffd4', fontSize: 11, fontWeight: '800' },
  miniRow: { flexDirection: 'row', gap: 7 },
  inputsRow: { flexDirection: 'row', gap: 7 },
  field: { flex: 1 },
  fieldLabel: { color: '#98b0a5', fontSize: 11, marginBottom: 3 },
  fieldInput: { borderWidth: 1, borderColor: '#264034', backgroundColor: '#0b0f0d', borderRadius: 10, color: '#e9fff3', fontSize: 13, paddingHorizontal: 8, paddingVertical: 8 },
  fixedProfitNote: { color: '#80a895', fontSize: 11, marginTop: -2 },
  speedRow: { flexDirection: 'row', alignItems: 'center', gap: 6, flexWrap: 'wrap' },
  speedChip: { borderWidth: 1, borderColor: '#2a4538', borderRadius: 999, paddingHorizontal: 10, paddingVertical: 6, backgroundColor: '#121816' },
  speedChipActive: { borderColor: '#00ff99', backgroundColor: '#113226' },
  speedText: { color: '#a8bdb3', fontSize: 12, fontWeight: '600' },
  speedTextActive: { color: '#00ff99' },
  customInput: { minWidth: 70, borderWidth: 1, borderColor: '#2a4538', borderRadius: 10, color: '#e9fff3', backgroundColor: '#0b0f0d', fontSize: 12, paddingHorizontal: 8, paddingVertical: 6 },
  switchRow: { flexDirection: 'row', gap: 6 },
  switchCell: { flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', backgroundColor: '#101815', borderWidth: 1, borderColor: '#213b2f', borderRadius: 10, paddingHorizontal: 8, paddingVertical: 6 },
  switchLabel: { color: '#d5eee2', fontSize: 12, fontWeight: '600' },
  btnRow: { flexDirection: 'row', gap: 7 },
  ctrlBtn: { flex: 1, borderRadius: 10, paddingVertical: 10, alignItems: 'center' },
  start: { backgroundColor: '#00c778' },
  stop: { backgroundColor: '#d54548' },
  logs: { backgroundColor: '#1f3029', borderWidth: 1, borderColor: '#305343' },
  ctrlText: { color: '#f4fff8', fontSize: 12, fontWeight: '800' },
  statsScroll: { gap: 7, paddingRight: 8 },
  stat: { minWidth: 88, borderWidth: 1, borderColor: '#234437', borderRadius: 10, backgroundColor: '#0d1512', paddingHorizontal: 8, paddingVertical: 7 },
  statWide: { minWidth: 150, borderWidth: 1, borderColor: '#234437', borderRadius: 10, backgroundColor: '#0d1512', paddingHorizontal: 8, paddingVertical: 7 },
  statL: { color: '#91a99e', fontSize: 10, marginBottom: 2 },
  statV: { color: '#00ff99', fontSize: 13, fontWeight: '700' },
  logPanel: { maxHeight: 110, borderWidth: 1, borderColor: '#22372c', borderRadius: 10, backgroundColor: '#090d0b', padding: 8 },
  logEmpty: { color: '#8ba399', fontSize: 12 },
  logRow: { borderBottomWidth: 1, borderBottomColor: '#16261f', paddingBottom: 5, marginBottom: 5 },
  logMsg: { fontSize: 12, fontWeight: '500' },
  neutral: { color: '#c8dfd3' },
  ok: { color: '#42f7ab' },
  bad: { color: '#ff7f82' },
  logT: { color: '#6d8377', fontSize: 10, marginTop: 1 },
  webWrap: { flex: 1, backgroundColor: '#000' },
  web: { flex: 1, backgroundColor: '#000' },
});


