import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  ActivityIndicator,
  Alert,
  Animated,
  Easing,
  Platform,
  Vibration,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  TouchableOpacity,
  View,
} from 'react-native';
import * as Application from 'expo-application';
import { Audio } from 'expo-av';
import * as Haptics from 'expo-haptics';
import * as Linking from 'expo-linking';
import axios from 'axios';

type Tone = 'neutral' | 'success' | 'danger';

type Banner = {
  message: string;
  tone: Tone;
};

type LogEntry = {
  id: string;
  message: string;
  tone: Tone;
  time: string;
};

type PriceStat = {
  count: number;
  success: number;
};

type BotStats = {
  ordersDetected: number;
  buyAttempts: number;
  ordersBought: number;
  estimatedProfit: number;
};

type BotSettings = {
  minPrice: number;
  maxPrice: number;
  minProfit: number;
  refreshSpeed: number;
};

type OrderCandidate = {
  price: number;
  reward: number;
  profitPercent: number;
};

const BASE_URL = 'https://arbsmartbot-b6rn.onrender.com';
const APP_SCHEME = 'myapp';
const PLAN_AMOUNT = 50;
const PLAN_CODE = 'daily';
const PRICE_BUCKET_SIZE = 50;
const MIN_REFRESH_SPEED = 50;
const MAX_REFRESH_SPEED = 500;
const BUY_COOLDOWN_MS = 1200;
const ALERT_TONE_URI =
  'data:audio/wav;base64,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';

const clamp = (value: number, min: number, max: number) => Math.min(Math.max(value, min), max);

const toNumber = (value: string, fallback: number) => {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
};

const randomInt = (min: number, max: number) => Math.floor(Math.random() * (max - min + 1)) + min;

const sleep = (ms: number) => new Promise<void>((resolve) => setTimeout(resolve, ms));

const getBucketStart = (price: number) => Math.floor(price / PRICE_BUCKET_SIZE) * PRICE_BUCKET_SIZE;

const formatBucketLabel = (bucketStart: number) =>
  `\u20B9${bucketStart} - \u20B9${bucketStart + PRICE_BUCKET_SIZE - 1}`;

const formatTimestamp = () =>
  new Date().toLocaleTimeString([], {
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });

const playWebBeep = (tone: Tone) => {
  if (Platform.OS !== 'web' || typeof window === 'undefined') {
    return;
  }

  const AudioContextCtor =
    window.AudioContext || (window as typeof window & { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;

  if (!AudioContextCtor) {
    return;
  }

  const audioContext = new AudioContextCtor();
  const oscillator = audioContext.createOscillator();
  const gain = audioContext.createGain();

  oscillator.type = tone === 'danger' ? 'sawtooth' : 'sine';
  oscillator.frequency.value = tone === 'success' ? 880 : tone === 'danger' ? 220 : 520;
  gain.gain.setValueAtTime(0.001, audioContext.currentTime);
  gain.gain.exponentialRampToValueAtTime(0.12, audioContext.currentTime + 0.02);
  gain.gain.exponentialRampToValueAtTime(0.001, audioContext.currentTime + 0.18);

  oscillator.connect(gain);
  gain.connect(audioContext.destination);
  oscillator.start();
  oscillator.stop(audioContext.currentTime + 0.2);
  oscillator.onended = () => {
    void audioContext.close();
  };
};

const playNativeBeep = async () => {
  if (Platform.OS === 'web') {
    return;
  }

  const { sound } = await Audio.Sound.createAsync({ uri: ALERT_TONE_URI }, { shouldPlay: true, volume: 1 });
  sound.setOnPlaybackStatusUpdate((status) => {
    if (status.isLoaded && status.didJustFinish) {
      void sound.unloadAsync();
    }
  });
};

export default function Index() {
  const [deviceId, setDeviceId] = useState('');
  const [subscriptionActive, setSubscriptionActive] = useState(false);
  const [checkingSubscription, setCheckingSubscription] = useState(true);
  const [hasCheckedOnce, setHasCheckedOnce] = useState(false);

  const [minPriceInput, setMinPriceInput] = useState('100');
  const [maxPriceInput, setMaxPriceInput] = useState('1500');
  const [minProfitInput, setMinProfitInput] = useState('12');
  const [refreshSpeedInput, setRefreshSpeedInput] = useState('120');

  const [smartMode, setSmartMode] = useState(true);
  const [safeMode, setSafeMode] = useState(false);
  const [soundEnabled, setSoundEnabled] = useState(false);
  const [botRunning, setBotRunning] = useState(false);

  const [stats, setStats] = useState<BotStats>({
    ordersDetected: 0,
    buyAttempts: 0,
    ordersBought: 0,
    estimatedProfit: 0,
  });
  const [bestPerformingPrice, setBestPerformingPrice] = useState('N/A');
  const [logItems, setLogItems] = useState<LogEntry[]>([]);
  const [banner, setBanner] = useState<Banner | null>(null);

  const bannerOpacity = useRef(new Animated.Value(0)).current;
  const bannerTranslateY = useRef(new Animated.Value(-10)).current;
  const bannerTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const smartModeRef = useRef(smartMode);
  const safeModeRef = useRef(safeMode);
  const soundEnabledRef = useRef(soundEnabled);

  const settingsRef = useRef<BotSettings>({
    minPrice: 100,
    maxPrice: 1500,
    minProfit: 12,
    refreshSpeed: 120,
  });
  const deviceIdRef = useRef('');

  const isRunningRef = useRef(false);
  const buyLockRef = useRef(false);
  const cooldownUntilRef = useRef(0);
  const loopTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const learningRef = useRef<Record<number, PriceStat>>({});

  const successRate = useMemo(() => {
    if (stats.buyAttempts === 0) {
      return 0;
    }
    return (stats.ordersBought / stats.buyAttempts) * 100;
  }, [stats.buyAttempts, stats.ordersBought]);

  useEffect(() => {
    settingsRef.current = {
      minPrice: Math.max(0, toNumber(minPriceInput, 100)),
      maxPrice: Math.max(0, toNumber(maxPriceInput, 1500)),
      minProfit: Math.max(0, toNumber(minProfitInput, 12)),
      refreshSpeed: clamp(toNumber(refreshSpeedInput, 120), MIN_REFRESH_SPEED, MAX_REFRESH_SPEED),
    };
  }, [minPriceInput, maxPriceInput, minProfitInput, refreshSpeedInput]);

  useEffect(() => {
    smartModeRef.current = smartMode;
  }, [smartMode]);

  useEffect(() => {
    safeModeRef.current = safeMode;
  }, [safeMode]);

  useEffect(() => {
    soundEnabledRef.current = soundEnabled;
  }, [soundEnabled]);

  useEffect(() => {
    deviceIdRef.current = deviceId;
  }, [deviceId]);

  const showBanner = useCallback(
    (message: string, tone: Tone) => {
      setBanner({ message, tone });

      if (bannerTimeoutRef.current) {
        clearTimeout(bannerTimeoutRef.current);
      }

      Animated.parallel([
        Animated.timing(bannerOpacity, {
          toValue: 1,
          duration: 200,
          easing: Easing.out(Easing.quad),
          useNativeDriver: true,
        }),
        Animated.timing(bannerTranslateY, {
          toValue: 0,
          duration: 200,
          easing: Easing.out(Easing.quad),
          useNativeDriver: true,
        }),
      ]).start();

      bannerTimeoutRef.current = setTimeout(() => {
        Animated.parallel([
          Animated.timing(bannerOpacity, {
            toValue: 0,
            duration: 180,
            easing: Easing.in(Easing.quad),
            useNativeDriver: true,
          }),
          Animated.timing(bannerTranslateY, {
            toValue: -10,
            duration: 180,
            easing: Easing.in(Easing.quad),
            useNativeDriver: true,
          }),
        ]).start(({ finished }) => {
          if (finished) {
            setBanner(null);
          }
        });
      }, 1700);
    },
    [bannerOpacity, bannerTranslateY]
  );

  const pushLog = useCallback((message: string, tone: Tone = 'neutral') => {
    setLogItems((prev) => {
      const next: LogEntry[] = [
        {
          id: `${Date.now()}-${Math.random()}`,
          message,
          tone,
          time: formatTimestamp(),
        },
        ...prev,
      ];

      return next.slice(0, 140);
    });
  }, []);

  const notify = useCallback(
    async (message: string, tone: Tone = 'neutral') => {
      showBanner(message, tone);

      if (!soundEnabledRef.current) {
        return;
      }

      try {
        playWebBeep(tone);
        void playNativeBeep();

        if (tone === 'success') {
          await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
        } else if (tone === 'danger') {
          await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
        } else {
          await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Warning);
        }

        if (Platform.OS !== 'web') {
          Vibration.vibrate(tone === 'danger' ? [0, 120, 80, 120] : 120);
        }
      } catch {
        // Ignore alert feedback errors (e.g., unsupported environments).
      }
    },
    [showBanner]
  );

  const resolveDeviceId = useCallback(async () => {
    if (Platform.OS === 'android') {
      const androidId = Application.getAndroidId();
      if (androidId) {
        return androidId;
      }
    }

    if (Platform.OS === 'ios') {
      const iosId = await Application.getIosIdForVendorAsync();
      if (iosId) {
        return iosId;
      }
    }

    return `device-${Application.applicationId ?? 'arb'}-${Date.now()}`;
  }, []);

  const checkSubscription = useCallback(
    async (source: 'launch' | 'manual' | 'deeplink' = 'launch') => {
      setCheckingSubscription(true);

      try {
        const currentId = deviceIdRef.current || (await resolveDeviceId());
        deviceIdRef.current = currentId;
        setDeviceId(currentId);

        const response = await axios.get(`${BASE_URL}/check`, {
          params: { device_id: currentId },
          timeout: 5000,
        });

        const active = Boolean(response.data?.active);
        setSubscriptionActive(active);

        if (source !== 'launch') {
          if (active) {
            await notify('Subscription verified. Dashboard unlocked.', 'success');
          } else {
            await notify('Subscription still inactive.', 'danger');
          }
        }
      } catch {
        setSubscriptionActive(false);
        if (source !== 'launch') {
          await notify('Network issue while checking subscription.', 'danger');
        }
      } finally {
        setHasCheckedOnce(true);
        setCheckingSubscription(false);
      }
    },
    [notify, resolveDeviceId]
  );

  const refreshBestPrice = useCallback(() => {
    const entries = Object.entries(learningRef.current);
    if (entries.length === 0) {
      setBestPerformingPrice('N/A');
      return;
    }

    let bestBucket = Number(entries[0][0]);
    let bestRate = 0;
    let bestCount = 0;

    for (const [bucketKey, value] of entries) {
      if (value.count === 0) {
        continue;
      }

      const rate = value.success / value.count;
      if (rate > bestRate || (Math.abs(rate - bestRate) < 0.0001 && value.count > bestCount)) {
        bestBucket = Number(bucketKey);
        bestRate = rate;
        bestCount = value.count;
      }
    }

    if (bestCount === 0) {
      setBestPerformingPrice('N/A');
      return;
    }

    setBestPerformingPrice(`${formatBucketLabel(bestBucket)} (${Math.round(bestRate * 100)}%)`);
  }, []);

  const updateLearning = useCallback(
    (price: number, success: boolean) => {
      const bucket = getBucketStart(price);
      const current = learningRef.current[bucket] ?? { count: 0, success: 0 };
      current.count += 1;
      if (success) {
        current.success += 1;
      }
      learningRef.current[bucket] = current;
      refreshBestPrice();
    },
    [refreshBestPrice]
  );

  const generateOrder = useCallback((): OrderCandidate => {
    const price = randomInt(80, 2500);
    const rewardMultiplier = 1 + randomInt(4, 52) / 100;
    const reward = Math.round(price * rewardMultiplier);
    const profitPercent = ((reward - price) / price) * 100;

    return { price, reward, profitPercent };
  }, []);

  const stopBot = useCallback(() => {
    isRunningRef.current = false;
    buyLockRef.current = false;
    setBotRunning(false);

    if (loopTimeoutRef.current) {
      clearTimeout(loopTimeoutRef.current);
      loopTimeoutRef.current = null;
    }

    pushLog('Bot stopped', 'danger');
    void notify('Bot stopped', 'danger');
  }, [notify, pushLog]);

  const runBotTick = useCallback(async () => {
    if (!isRunningRef.current) {
      return;
    }

    const settings = settingsRef.current;
    const scansThisCycle = Math.random() < 0.7 ? 1 : 2;

    for (let i = 0; i < scansThisCycle; i += 1) {
      if (!isRunningRef.current) {
        return;
      }

      const order = generateOrder();

      setStats((prev) => ({
        ...prev,
        ordersDetected: prev.ordersDetected + 1,
      }));

      pushLog(`Order \u20B9${order.price} detected`, 'neutral');
      void notify(`Order detected: \u20B9${order.price}`, 'neutral');

      if (order.price < settings.minPrice || order.price > settings.maxPrice) {
        pushLog(`Skipped (outside range): \u20B9${order.price}`, 'neutral');
        continue;
      }

      if (order.profitPercent < settings.minProfit) {
        pushLog(`Skipped (low profit ${order.profitPercent.toFixed(1)}%)`, 'neutral');
        continue;
      }

      const bucketStart = getBucketStart(order.price);
      let prioritized = false;

      if (smartModeRef.current) {
        const bucketData = learningRef.current[bucketStart];

        if (bucketData && bucketData.count >= 3) {
          const bucketSuccessRate = (bucketData.success / bucketData.count) * 100;

          if (bucketSuccessRate < 30) {
            pushLog(
              `Skipped (smart mode: ${Math.round(bucketSuccessRate)}% at ${formatBucketLabel(bucketStart)})`,
              'danger'
            );
            continue;
          }

          if (bucketSuccessRate > 60) {
            prioritized = true;
            pushLog(`Priority boost on ${formatBucketLabel(bucketStart)}`, 'success');
          }
        }
      }

      if (Date.now() < cooldownUntilRef.current) {
        pushLog('Skipped (cooldown active)', 'neutral');
        continue;
      }

      if (buyLockRef.current) {
        pushLog('Skipped (buy lock active)', 'neutral');
        continue;
      }

      buyLockRef.current = true;

      try {
        if (safeModeRef.current) {
          await sleep(randomInt(120, 380));
        }

        await sleep(randomInt(20, 80));

        const successChance = prioritized ? 0.86 : 0.68;
        const isBuySuccess = Math.random() < successChance;

        setStats((prev) => ({
          ...prev,
          buyAttempts: prev.buyAttempts + 1,
          ordersBought: prev.ordersBought + (isBuySuccess ? 1 : 0),
          estimatedProfit: prev.estimatedProfit + (isBuySuccess ? Math.max(order.reward - order.price, 0) : 0),
        }));

        updateLearning(order.price, isBuySuccess);

        if (isBuySuccess) {
          pushLog(`Bought successfully at \u20B9${order.price}`, 'success');
          void notify('Buy success', 'success');
          cooldownUntilRef.current = Date.now() + BUY_COOLDOWN_MS;
        } else {
          pushLog(`Buy failed at \u20B9${order.price}`, 'danger');
          void notify('Buy failed', 'danger');
        }
      } finally {
        buyLockRef.current = false;
      }
    }

    if (isRunningRef.current) {
      loopTimeoutRef.current = setTimeout(() => {
        void runBotTick();
      }, settingsRef.current.refreshSpeed);
    }
  }, [generateOrder, notify, pushLog, updateLearning]);

  const startBot = useCallback(() => {
    if (!subscriptionActive) {
      Alert.alert('Subscription Required', 'Please activate your plan to run the bot.');
      return;
    }

    if (isRunningRef.current) {
      return;
    }

    const settings = settingsRef.current;

    if (settings.maxPrice < settings.minPrice) {
      Alert.alert('Invalid Range', 'Max Price must be greater than or equal to Min Price.');
      return;
    }

    isRunningRef.current = true;
    setBotRunning(true);

    pushLog(`Bot started (${settings.refreshSpeed}ms refresh)`, 'success');
    void notify('Bot started', 'success');

    void runBotTick();
  }, [notify, pushLog, runBotTick, subscriptionActive]);

  const normalizeRefreshInput = useCallback(() => {
    const normalized = clamp(toNumber(refreshSpeedInput, 120), MIN_REFRESH_SPEED, MAX_REFRESH_SPEED);
    setRefreshSpeedInput(String(normalized));
  }, [refreshSpeedInput]);

  const handleBuyNow = useCallback(async () => {
    const id = deviceId || (await resolveDeviceId());
    if (!deviceId) {
      setDeviceId(id);
    }

    try {
      const response = await axios.post(
        `${BASE_URL}/payment/init`,
        {
          device_id: id,
          amount: PLAN_AMOUNT,
          plan_code: PLAN_CODE,
          phone: 'NULL',
        },
        { timeout: 7000 }
      );

      const paymentUrl = response.data?.payment_url;
      if (!paymentUrl || typeof paymentUrl !== 'string') {
        throw new Error('Missing payment URL');
      }

      await Linking.openURL(paymentUrl);
    } catch {
      Alert.alert('Payment Error', 'Unable to open payment page. Please try again.');
    }
  }, [deviceId, resolveDeviceId]);

  const handleDeepLink = useCallback(
    async (url: string) => {
      const parsed = Linking.parse(url);
      const isPaymentSuccess =
        url.toLowerCase().startsWith(`${APP_SCHEME}://payment-success`) || parsed.path === 'payment-success';

      if (!isPaymentSuccess) {
        return;
      }

      pushLog('Payment success callback received', 'success');
      await notify('Payment success detected. Verifying...', 'neutral');
      await checkSubscription('deeplink');
    },
    [checkSubscription, notify, pushLog]
  );

  useEffect(() => {
    void checkSubscription('launch');
  }, [checkSubscription]);

  useEffect(() => {
    void Linking.getInitialURL().then((url) => {
      if (url) {
        void handleDeepLink(url);
      }
    });

    const sub = Linking.addEventListener('url', ({ url }) => {
      void handleDeepLink(url);
    });

    return () => {
      sub.remove();
    };
  }, [handleDeepLink]);

  useEffect(() => {
    return () => {
      if (loopTimeoutRef.current) {
        clearTimeout(loopTimeoutRef.current);
      }

      if (bannerTimeoutRef.current) {
        clearTimeout(bannerTimeoutRef.current);
      }
    };
  }, []);

  if (!hasCheckedOnce && checkingSubscription) {
    return (
      <View style={styles.screenCenter}>
        <ActivityIndicator size="large" color="#00ff99" />
        <Text style={styles.loadingText}>Checking subscription...</Text>
      </View>
    );
  }

  const statusLabel = botRunning ? 'Running \u2705' : 'Inactive \u274C';
  const statusColor = botRunning ? '#00ff99' : '#ff4d4f';

  return (
    <View style={styles.screen}>
      {banner ? (
        <Animated.View
          style={[
            styles.banner,
            banner.tone === 'success'
              ? styles.bannerSuccess
              : banner.tone === 'danger'
                ? styles.bannerDanger
                : styles.bannerNeutral,
            {
              opacity: bannerOpacity,
              transform: [{ translateY: bannerTranslateY }],
            },
          ]}>
          <Text style={styles.bannerText}>{banner.message}</Text>
        </Animated.View>
      ) : null}

      {!subscriptionActive ? (
        <View style={styles.planWrapper}>
          <View style={styles.planCard}>
            <Text style={styles.planTitle}>Subscription Plan</Text>
            <Text style={styles.planPrice}>{'\u20B9'}50</Text>
            <Text style={styles.planValidity}>Valid for 1 Day</Text>

            <TouchableOpacity style={styles.primaryButton} onPress={() => void handleBuyNow()}>
              <Text style={styles.primaryButtonText}>Buy Now</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={styles.secondaryButton}
              onPress={() => void checkSubscription('manual')}
              disabled={checkingSubscription}>
              <Text style={styles.secondaryButtonText}>
                {checkingSubscription ? 'Checking...' : 'I Paid, Recheck'}
              </Text>
            </TouchableOpacity>

            <Text style={styles.planHint}>Single plan: {'\u20B9'}50 per day</Text>
            <Text style={styles.deviceHint} numberOfLines={1}>
              Device ID: {deviceId || 'loading...'}
            </Text>
            <Text style={styles.deepLinkHint}>Expected callback: myapp://payment-success</Text>
          </View>
        </View>
      ) : (
        <ScrollView contentContainerStyle={styles.content}>
          <View style={styles.headerCard}>
            <Text style={styles.headerTitle}>ARB Smart Bot</Text>
            <Text style={[styles.headerStatus, { color: statusColor }]}>{statusLabel}</Text>
          </View>

          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Controls</Text>

            <View style={styles.inputRow}>
              <Text style={styles.inputLabel}>Min Price</Text>
              <TextInput
                value={minPriceInput}
                onChangeText={setMinPriceInput}
                keyboardType="number-pad"
                style={styles.input}
                placeholder="100"
                placeholderTextColor="#647067"
              />
            </View>

            <View style={styles.inputRow}>
              <Text style={styles.inputLabel}>Max Price</Text>
              <TextInput
                value={maxPriceInput}
                onChangeText={setMaxPriceInput}
                keyboardType="number-pad"
                style={styles.input}
                placeholder="1500"
                placeholderTextColor="#647067"
              />
            </View>

            <View style={styles.inputRow}>
              <Text style={styles.inputLabel}>Min Profit %</Text>
              <TextInput
                value={minProfitInput}
                onChangeText={setMinProfitInput}
                keyboardType="number-pad"
                style={styles.input}
                placeholder="12"
                placeholderTextColor="#647067"
              />
            </View>

            <View style={styles.inputRow}>
              <Text style={styles.inputLabel}>Refresh Speed (50-500ms)</Text>
              <TextInput
                value={refreshSpeedInput}
                onChangeText={setRefreshSpeedInput}
                onBlur={normalizeRefreshInput}
                keyboardType="number-pad"
                style={styles.input}
                placeholder="120"
                placeholderTextColor="#647067"
              />
            </View>

            <View style={styles.buttonRow}>
              <TouchableOpacity
                style={[styles.actionButton, styles.startButton, botRunning && styles.disabledButton]}
                onPress={startBot}
                disabled={botRunning}>
                <Text style={styles.actionButtonText}>Start Bot</Text>
              </TouchableOpacity>
              <TouchableOpacity
                style={[styles.actionButton, styles.stopButton, !botRunning && styles.disabledButton]}
                onPress={stopBot}
                disabled={!botRunning}>
                <Text style={styles.actionButtonText}>Stop Bot</Text>
              </TouchableOpacity>
            </View>
          </View>

          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Adaptive & Safety</Text>

            <View style={styles.switchRow}>
              <Text style={styles.switchLabel}>Smart Adaptive Mode</Text>
              <Switch
                value={smartMode}
                onValueChange={setSmartMode}
                trackColor={{ false: '#3a3a3a', true: '#00b86f' }}
                thumbColor={smartMode ? '#00ff99' : '#b1b1b1'}
              />
            </View>

            <View style={styles.switchRow}>
              <Text style={styles.switchLabel}>Safe Mode (random delay)</Text>
              <Switch
                value={safeMode}
                onValueChange={setSafeMode}
                trackColor={{ false: '#3a3a3a', true: '#00b86f' }}
                thumbColor={safeMode ? '#00ff99' : '#b1b1b1'}
              />
            </View>

            <View style={styles.switchRow}>
              <Text style={styles.switchLabel}>Alert Sound / Haptics</Text>
              <Switch
                value={soundEnabled}
                onValueChange={setSoundEnabled}
                trackColor={{ false: '#3a3a3a', true: '#00b86f' }}
                thumbColor={soundEnabled ? '#00ff99' : '#b1b1b1'}
              />
            </View>
          </View>

          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Stats Dashboard</Text>
            <View style={styles.statsGrid}>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Orders Detected</Text>
                <Text style={styles.statValue}>{stats.ordersDetected}</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Orders Bought</Text>
                <Text style={styles.statValue}>{stats.ordersBought}</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Success Rate</Text>
                <Text style={styles.statValue}>{successRate.toFixed(1)}%</Text>
              </View>
              <View style={styles.statCard}>
                <Text style={styles.statLabel}>Estimated Profit</Text>
                <Text style={styles.statValue}>{'\u20B9'} {stats.estimatedProfit}</Text>
              </View>
            </View>

            <View style={styles.bestPriceBox}>
              <Text style={styles.bestPriceLabel}>Best Performing Price</Text>
              <Text style={styles.bestPriceValue}>{bestPerformingPrice}</Text>
            </View>
          </View>

          <View style={styles.card}>
            <Text style={styles.sectionTitle}>Activity Log</Text>
            <View style={styles.logContainer}>
              {logItems.length === 0 ? (
                <Text style={styles.logEmpty}>No activity yet. Start the bot to begin scanning.</Text>
              ) : (
                <ScrollView showsVerticalScrollIndicator={false}>
                  {logItems.map((item) => (
                    <View key={item.id} style={styles.logRow}>
                      <Text
                        style={[
                          styles.logMessage,
                          item.tone === 'success'
                            ? styles.logSuccess
                            : item.tone === 'danger'
                              ? styles.logDanger
                              : styles.logNeutral,
                        ]}>
                        {item.message}
                      </Text>
                      <Text style={styles.logTime}>{item.time}</Text>
                    </View>
                  ))}
                </ScrollView>
              )}
            </View>
          </View>

          <View style={styles.footerInfo}>
            <Text style={styles.footerText}>Pricing: {'\u20B9'}50 per day</Text>
            <Text style={styles.footerText}>Access: Device-based, no login required</Text>
          </View>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  screen: {
    flex: 1,
    backgroundColor: '#000000',
  },
  screenCenter: {
    flex: 1,
    backgroundColor: '#000000',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  loadingText: {
    marginTop: 10,
    color: '#b5c2bb',
    fontSize: 15,
  },
  banner: {
    position: 'absolute',
    zIndex: 20,
    top: 12,
    left: 12,
    right: 12,
    borderRadius: 12,
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderWidth: 1,
  },
  bannerSuccess: {
    backgroundColor: '#052014',
    borderColor: '#00ff99',
  },
  bannerDanger: {
    backgroundColor: '#2a0d10',
    borderColor: '#ff4d4f',
  },
  bannerNeutral: {
    backgroundColor: '#102218',
    borderColor: '#2f5f48',
  },
  bannerText: {
    color: '#ebfff4',
    fontWeight: '600',
    textAlign: 'center',
    fontSize: 13,
  },
  planWrapper: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 20,
  },
  planCard: {
    width: '100%',
    maxWidth: 440,
    backgroundColor: '#111111',
    borderRadius: 18,
    paddingVertical: 28,
    paddingHorizontal: 20,
    borderWidth: 1,
    borderColor: '#1f3a2d',
    alignItems: 'center',
  },
  planTitle: {
    color: '#ecfef3',
    fontSize: 25,
    fontWeight: '700',
    marginBottom: 10,
  },
  planPrice: {
    color: '#00ff99',
    fontSize: 36,
    fontWeight: '800',
    marginBottom: 4,
  },
  planValidity: {
    color: '#8ea99b',
    fontSize: 15,
    marginBottom: 22,
  },
  primaryButton: {
    width: '100%',
    borderRadius: 12,
    backgroundColor: '#00ff99',
    paddingVertical: 13,
    alignItems: 'center',
    marginBottom: 10,
  },
  primaryButtonText: {
    color: '#05120d',
    fontWeight: '800',
    fontSize: 15,
  },
  secondaryButton: {
    width: '100%',
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#2e5a45',
    backgroundColor: '#121f18',
    paddingVertical: 12,
    alignItems: 'center',
    marginBottom: 14,
  },
  secondaryButtonText: {
    color: '#cceadd',
    fontWeight: '700',
    fontSize: 14,
  },
  planHint: {
    fontSize: 12,
    color: '#6f877b',
    textAlign: 'center',
    marginBottom: 8,
  },
  deviceHint: {
    fontSize: 11,
    color: '#90a79b',
    marginBottom: 4,
    width: '100%',
    textAlign: 'center',
  },
  deepLinkHint: {
    fontSize: 11,
    color: '#90a79b',
  },
  content: {
    paddingTop: 48,
    paddingHorizontal: 14,
    paddingBottom: 28,
  },
  headerCard: {
    backgroundColor: '#0d1310',
    borderColor: '#1c3d2d',
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
    marginBottom: 12,
  },
  headerTitle: {
    color: '#f0fff5',
    fontSize: 28,
    fontWeight: '800',
  },
  headerStatus: {
    marginTop: 6,
    fontSize: 15,
    fontWeight: '700',
  },
  card: {
    backgroundColor: '#101010',
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#1f2a23',
    padding: 14,
    marginBottom: 12,
  },
  sectionTitle: {
    color: '#e8fff2',
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 12,
  },
  inputRow: {
    marginBottom: 10,
  },
  inputLabel: {
    color: '#9ab0a5',
    fontSize: 13,
    marginBottom: 5,
    fontWeight: '600',
  },
  input: {
    borderWidth: 1,
    borderColor: '#28372e',
    backgroundColor: '#0b0f0d',
    color: '#e8fff2',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 15,
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 10,
    marginTop: 4,
  },
  actionButton: {
    flex: 1,
    borderRadius: 12,
    paddingVertical: 13,
    alignItems: 'center',
  },
  startButton: {
    backgroundColor: '#00cc7d',
  },
  stopButton: {
    backgroundColor: '#d33f45',
  },
  disabledButton: {
    opacity: 0.5,
  },
  actionButtonText: {
    color: '#f5fff8',
    fontWeight: '700',
    fontSize: 14,
  },
  switchRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 6,
    gap: 10,
  },
  switchLabel: {
    color: '#cde5d9',
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  statCard: {
    width: '48%',
    borderWidth: 1,
    borderColor: '#24382d',
    borderRadius: 12,
    paddingVertical: 12,
    paddingHorizontal: 10,
    backgroundColor: '#0b0f0d',
  },
  statLabel: {
    color: '#93ab9f',
    fontSize: 12,
    marginBottom: 6,
  },
  statValue: {
    color: '#00ff99',
    fontSize: 21,
    fontWeight: '700',
  },
  bestPriceBox: {
    marginTop: 12,
    borderWidth: 1,
    borderColor: '#24523d',
    borderRadius: 12,
    backgroundColor: '#0b1712',
    paddingVertical: 10,
    paddingHorizontal: 12,
  },
  bestPriceLabel: {
    color: '#8fb6a2',
    fontSize: 12,
    marginBottom: 3,
  },
  bestPriceValue: {
    color: '#d9ffee',
    fontSize: 14,
    fontWeight: '700',
  },
  logContainer: {
    borderWidth: 1,
    borderColor: '#22372c',
    borderRadius: 12,
    backgroundColor: '#090c0b',
    minHeight: 180,
    maxHeight: 260,
    padding: 10,
  },
  logEmpty: {
    color: '#8ba399',
    fontSize: 13,
  },
  logRow: {
    borderBottomWidth: 1,
    borderBottomColor: '#1b2a22',
    paddingBottom: 7,
    marginBottom: 7,
  },
  logMessage: {
    fontSize: 13,
    fontWeight: '500',
  },
  logNeutral: {
    color: '#c8dfd3',
  },
  logSuccess: {
    color: '#42f7ab',
  },
  logDanger: {
    color: '#ff7f82',
  },
  logTime: {
    color: '#6d8377',
    fontSize: 11,
    marginTop: 2,
  },
  footerInfo: {
    marginTop: 2,
    alignItems: 'center',
    gap: 4,
  },
  footerText: {
    color: '#7f968b',
    fontSize: 12,
  },
});
