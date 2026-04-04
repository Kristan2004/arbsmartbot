const express = require('express');
const cors = require('cors');
const { createClient } = require('@supabase/supabase-js');

const app = express();
app.use(cors());
app.use(express.json());

const SUPABASE_URL = process.env.SUPABASE_URL || 'https://tbaxmuocueirpgzdjpbv.supabase.co/';
const SUPABASE_KEY =
  process.env.SUPABASE_SERVICE_ROLE_KEY ||
  process.env.SUPABASE_ANON_KEY ||
  'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRiYXhtdW9jdWVpcnBnemRqcGJ2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzQ4NDAyMzUsImV4cCI6MjA5MDQxNjIzNX0.eZa0h_5INfbKH4PrOzZyGx6WEwvZG-gLI5YZG8D09FM';

const PLAN_WEEKLY = {
  code: '5d',
  amount: 50,
  durationMs: 5 * 24 * 60 * 60 * 1000,
  label: '5 Days',
};
const PLAN_HOURLY = {
  code: '1h',
  amount: 10,
  durationMs: 60 * 60 * 1000,
  label: '1 Hour',
};
const DEFAULT_PLAN = PLAN_WEEKLY;
const PLAN_AMOUNT = DEFAULT_PLAN.amount;
const DEFAULT_PHONE = 'NULL';
const DEPOSIT_UTR_PLACEHOLDER = '00000000000';
const PAYMENT_BASE_URL = 'https://chainfabric.blogspot.com/';
const CASHFREE_API_BASE = process.env.CASHFREE_API_BASE || 'https://api.cashfree.com';
const CASHFREE_API_VERSION = process.env.CASHFREE_API_VERSION || '2023-08-01';
const CASHFREE_APP_ID = process.env.CASHFREE_APP_ID || '';
const CASHFREE_SECRET_KEY = process.env.CASHFREE_SECRET_KEY || '';
const CASHFREE_DEFAULT_PHONE = process.env.CASHFREE_DEFAULT_PHONE || '9999999999';

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const addMs = (date, ms) => new Date(new Date(date).getTime() + ms);
const PLAN_CODE_ALIASES = {
  '5d': PLAN_WEEKLY.code,
  weekly: PLAN_WEEKLY.code,
  '5days': PLAN_WEEKLY.code,
  pro: PLAN_WEEKLY.code,
  pro_5d: PLAN_WEEKLY.code,
  '1h': PLAN_HOURLY.code,
  hourly: PLAN_HOURLY.code,
  quick: PLAN_HOURLY.code,
  quick_1h: PLAN_HOURLY.code,
};
const PLAN_BY_CODE = {
  [PLAN_WEEKLY.code]: PLAN_WEEKLY,
  [PLAN_HOURLY.code]: PLAN_HOURLY,
};
const ALL_PLANS = [PLAN_WEEKLY, PLAN_HOURLY];

const normalizePlanCode = (value) => {
  const key = String(value || '').trim().toLowerCase();
  if (!key) return null;
  return PLAN_CODE_ALIASES[key] || null;
};

const resolvePlanFromAmount = (amount) => {
  const parsed = Number(amount);
  if (!Number.isFinite(parsed) || parsed <= 0) return DEFAULT_PLAN;
  const plan = ALL_PLANS.find((entry) => entry.amount === parsed);
  return plan || DEFAULT_PLAN;
};

const resolvePlan = (planCode, amount) => {
  const normalizedCode = normalizePlanCode(planCode);
  if (normalizedCode && PLAN_BY_CODE[normalizedCode]) return PLAN_BY_CODE[normalizedCode];
  return resolvePlanFromAmount(amount);
};

const getExpiryForPlan = (start, plan) => addMs(start, plan.durationMs);

const parseSqlDateValue = (text) => {
  const m = String(text || '').match(
    /^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,6}))?(?:\s*(Z|[+-]\d{2}:?\d{2}|[+-]\d{2}))?$/
  );
  if (!m) return null;

  const year = Number(m[1]);
  const month = Number(m[2]) - 1;
  const day = Number(m[3]);
  const hour = Number(m[4]);
  const minute = Number(m[5]);
  const second = Number(m[6]);
  const msRaw = (m[7] || '').padEnd(3, '0').slice(0, 3);
  const millis = Number(msRaw || 0);
  const tz = m[8];

  if (!tz) {
    const local = new Date(year, month, day, hour, minute, second, millis);
    return Number.isNaN(local.getTime()) ? null : local;
  }

  if (tz.toUpperCase() === 'Z') {
    return new Date(Date.UTC(year, month, day, hour, minute, second, millis));
  }

  const sign = tz.startsWith('-') ? -1 : 1;
  const body = tz.slice(1).replace(':', '');
  const tzHour = Number(body.slice(0, 2) || '0');
  const tzMinute = Number(body.slice(2, 4) || '0');
  const offsetMs = (tzHour * 60 + tzMinute) * 60 * 1000;
  return new Date(Date.UTC(year, month, day, hour, minute, second, millis) - sign * offsetMs);
};

const parseDateValue = (value) => {
  if (value instanceof Date && !Number.isNaN(value.getTime())) return value;
  if (typeof value === 'number' && Number.isFinite(value)) {
    const ms = value > 1e12 ? value : value > 1e9 ? value * 1000 : NaN;
    if (Number.isFinite(ms)) return new Date(ms);
  }
  if (typeof value === 'string') {
    const text = value.trim();
    if (!text) return null;
    const asNum = Number(text);
    if (Number.isFinite(asNum)) return parseDateValue(asNum);
    const normalized = text.includes('T') ? text : text.replace(' ', 'T');
    const parsed = Date.parse(normalized);
    if (Number.isFinite(parsed)) return new Date(parsed);
    const strict = parseSqlDateValue(text);
    if (strict) return strict;
  }
  return null;
};

const isValidDate = (value) => parseDateValue(value) !== null;
const isFutureDate = (value) => {
  const parsed = parseDateValue(value);
  return parsed !== null && parsed > new Date();
};
const remainingSeconds = (value) => {
  const parsed = parseDateValue(value);
  if (!parsed) return 0;
  return Math.max(0, Math.floor((parsed.getTime() - Date.now()) / 1000));
};

const generateOrderId = (prefix = 'ACT') => `${prefix}-${Date.now()}-${Math.floor(100 + Math.random() * 900)}`;

const parseJsonSafe = (raw) => {
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
};

const sanitizePhone = (rawPhone) => {
  const digits = String(rawPhone || '').replace(/\D/g, '');
  if (digits.length >= 10) {
    return digits.slice(-10);
  }
  return CASHFREE_DEFAULT_PHONE;
};

const sanitizeCustomerId = (rawId) => {
  const cleaned = String(rawId || '').replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 45);
  return cleaned || `cust${Date.now()}`;
};

const cashfreeHeaders = () => ({
  'Content-Type': 'application/json',
  'x-api-version': CASHFREE_API_VERSION,
  'x-client-id': CASHFREE_APP_ID,
  'x-client-secret': CASHFREE_SECRET_KEY,
});

async function getOrCreateSubscriptionByDevice(deviceId) {
  const { data, error } = await supabase
    .from('subscriptions')
    .select('id, device_id, status, expiry, order_id, amount')
    .eq('device_id', deviceId)
    .limit(1)
    .maybeSingle();

  if (error) {
    return { subscription: null, error };
  }

  if (data) {
    return { subscription: data, error: null };
  }

  const inactivePayload = {
    device_id: deviceId,
    status: 'inactive',
    order_id: generateOrderId('INIT'),
    expiry: new Date(0).toISOString(),
    amount: 0,
  };

  const { data: created, error: createError } = await supabase
    .from('subscriptions')
    .insert([inactivePayload])
    .select('id, device_id, status, expiry, order_id, amount')
    .single();

  if (createError) {
    // Handle race where another request created the row first.
    const { data: fallback, error: fallbackError } = await supabase
      .from('subscriptions')
      .select('id, device_id, status, expiry, order_id, amount')
      .eq('device_id', deviceId)
      .limit(1)
      .maybeSingle();

    if (fallbackError) {
      return { subscription: null, error: createError };
    }

    if (fallback) {
      return { subscription: fallback, error: null };
    }

    return { subscription: null, error: createError };
  }

  return { subscription: created, error: null };
}

async function getLatestActivationDeposit(subscriptionUuid) {
  const { data, error } = await supabase
    .from('deposits')
    .select('order_id, amount, created_at, status, UTR')
    .eq('user_id', subscriptionUuid)
    .eq('status', 'success')
    .like('order_id', 'ACT-%')
    .order('created_at', { ascending: false })
    .limit(1)
    .maybeSingle();

  if (error) {
    return { deposit: null, error };
  }

  return { deposit: data, error: null };
}

async function setSubscriptionSuccess(subscriptionId, orderId, amount, expiryIso) {
  const { error } = await supabase
    .from('subscriptions')
    .update({
      status: 'success',
      order_id: orderId,
      amount,
      expiry: expiryIso,
    })
    .eq('id', subscriptionId);

  return { error };
}

app.get('/check', async (req, res) => {
  try {
    const { device_id } = req.query;

    if (!device_id || typeof device_id !== 'string') {
      return res.status(400).json({ active: false, error: 'device_id is required' });
    }

    const { subscription, error } = await getOrCreateSubscriptionByDevice(device_id);

    if (error || !subscription) {
      console.error('Failed to resolve subscription row:', error);
      return res.status(500).json({ active: false });
    }

    const subscriptionExpiry = parseDateValue(subscription.expiry);
    let lastKnownExpiry = subscriptionExpiry;
    if (subscription.status === 'success' && isFutureDate(subscriptionExpiry)) {
      return res.json({
        active: true,
        source: 'subscriptions',
        subscription_uuid: subscription.id,
        expiry: subscriptionExpiry ? subscriptionExpiry.toISOString() : null,
        expiry_raw: subscription.expiry ?? null,
        remaining_seconds: remainingSeconds(subscriptionExpiry),
      });
    }

    const { deposit, error: depositError } = await getLatestActivationDeposit(subscription.id);

    if (depositError) {
      console.error('Failed to check deposits fallback:', depositError);
      return res.status(500).json({ active: false, subscription_uuid: subscription.id });
    }

    if (deposit) {
      const createdAt = parseDateValue(deposit.created_at);
      const fallbackPlan = resolvePlan(null, Number(deposit.amount));
      const fallbackExpiry = createdAt ? getExpiryForPlan(createdAt, fallbackPlan) : null;
      if (fallbackExpiry) lastKnownExpiry = fallbackExpiry;

      if (isFutureDate(fallbackExpiry)) {
        const { error: syncError } = await setSubscriptionSuccess(
          subscription.id,
          deposit.order_id,
          Number(deposit.amount) || fallbackPlan.amount,
          fallbackExpiry.toISOString()
        );

        if (syncError) {
          console.error('Failed to sync subscription from deposits:', syncError);
        }

        return res.json({
          active: true,
          source: 'deposits',
          subscription_uuid: subscription.id,
          expiry: fallbackExpiry ? fallbackExpiry.toISOString() : null,
          expiry_raw: subscription.expiry ?? null,
          remaining_seconds: remainingSeconds(fallbackExpiry),
        });
      }
    }

    if (subscription.status !== 'inactive') {
      await supabase.from('subscriptions').update({ status: 'inactive' }).eq('id', subscription.id);
    }

    return res.json({
      active: false,
      subscription_uuid: subscription.id,
      expiry: lastKnownExpiry ? lastKnownExpiry.toISOString() : null,
      expiry_raw: subscription.expiry ?? null,
      remaining_seconds: lastKnownExpiry ? remainingSeconds(lastKnownExpiry) : 0,
    });
  } catch (err) {
    console.error('Unexpected /check error:', err);
    return res.status(500).json({ active: false });
  }
});

app.post('/payment/init', async (req, res) => {
  try {
    const { device_id, amount, plan_code, phone } = req.body || {};

    if (!device_id || typeof device_id !== 'string') {
      return res.status(400).json({ success: false, error: 'device_id is required' });
    }

    const selectedPlan = resolvePlan(plan_code, amount);
    const paymentAmount = selectedPlan.amount;

    const { subscription, error: subscriptionError } = await getOrCreateSubscriptionByDevice(device_id);

    if (subscriptionError || !subscription) {
      console.error('Failed to resolve subscription for payment init:', subscriptionError);
      return res.status(500).json({ success: false, error: 'subscription lookup failed' });
    }

    const orderId = generateOrderId('ACT');

    const { error: depositError } = await supabase.from('deposits').insert([
      {
        user_id: subscription.id,
        amount: paymentAmount,
        order_id: orderId,
        status: 'pending',
        UTR: DEPOSIT_UTR_PLACEHOLDER,
      },
    ]);

    if (depositError) {
      console.error('Failed to create deposit row:', depositError);
      return res.status(500).json({ success: false, error: 'deposit create failed' });
    }

    const paymentUrl =
      `${PAYMENT_BASE_URL}?username=${encodeURIComponent(device_id)}` +
      `&uuid=${encodeURIComponent(subscription.id)}` +
      `&amount=${encodeURIComponent(String(paymentAmount))}` +
      `&plan_code=${encodeURIComponent(selectedPlan.code)}` +
      `&order_id=${encodeURIComponent(orderId)}` +
      `&phone=${encodeURIComponent(typeof phone === 'string' && phone.trim() ? phone.trim() : DEFAULT_PHONE)}` +
      '&type=activation';

    return res.json({
      success: true,
      payment_url: paymentUrl,
      order_id: orderId,
      subscription_uuid: subscription.id,
      amount: paymentAmount,
      plan_code: selectedPlan.code,
      plan_validity: selectedPlan.label,
    });
  } catch (err) {
    console.error('Unexpected /payment/init error:', err);
    return res.status(500).json({ success: false });
  }
});

app.post('/payment/create-order', async (req, res) => {
  try {
    const { order_id, user_id, amount, phone, return_url } = req.body || {};

    if (!order_id || !user_id) {
      return res.status(400).json({ success: false, error: 'order_id and user_id are required' });
    }

    if (!CASHFREE_APP_ID || !CASHFREE_SECRET_KEY) {
      return res.status(500).json({
        success: false,
        error: 'Cashfree credentials missing on backend',
      });
    }

    const { data: deposit, error: depositError } = await supabase
      .from('deposits')
      .select('order_id, amount, status, user_id')
      .eq('order_id', order_id)
      .eq('user_id', user_id)
      .limit(1)
      .maybeSingle();

    if (depositError) {
      console.error('Failed to validate deposit before create-order:', depositError);
      return res.status(500).json({ success: false, error: 'deposit lookup failed' });
    }

    if (!deposit) {
      return res.status(404).json({ success: false, error: 'matching deposit row not found' });
    }

    const resolvedAmount =
      Number.isFinite(Number(deposit.amount)) && Number(deposit.amount) > 0
        ? Number(deposit.amount)
        : Number.isFinite(Number(amount)) && Number(amount) > 0
          ? Number(amount)
          : PLAN_AMOUNT;

    const resolvedReturnUrl =
      typeof return_url === 'string' && return_url.trim()
        ? return_url.trim()
        : `${PAYMENT_BASE_URL}?order_id=${encodeURIComponent(order_id)}&uuid=${encodeURIComponent(user_id)}&amount=${encodeURIComponent(String(resolvedAmount))}`;

    const cashfreePayload = {
      order_id,
      order_amount: resolvedAmount,
      order_currency: 'INR',
      customer_details: {
        customer_id: sanitizeCustomerId(user_id),
        customer_phone: sanitizePhone(phone),
        customer_name: 'ARB User',
      },
      order_meta: {
        return_url: resolvedReturnUrl,
      },
    };

    const cashfreeResponse = await fetch(`${CASHFREE_API_BASE}/pg/orders`, {
      method: 'POST',
      headers: cashfreeHeaders(),
      body: JSON.stringify(cashfreePayload),
    });

    const raw = await cashfreeResponse.text();
    const parsed = parseJsonSafe(raw);

    if (!cashfreeResponse.ok) {
      const code = parsed?.code || parsed?.error_code || parsed?.type;
      const isOrderAlreadyExists =
        cashfreeResponse.status === 409 &&
        typeof code === 'string' &&
        code.toLowerCase().includes('order_already_exists');

      if (isOrderAlreadyExists) {
        const existingOrderResponse = await fetch(
          `${CASHFREE_API_BASE}/pg/orders/${encodeURIComponent(order_id)}`,
          {
            method: 'GET',
            headers: cashfreeHeaders(),
          }
        );

        const existingRaw = await existingOrderResponse.text();
        const existingParsed = parseJsonSafe(existingRaw);

        if (existingOrderResponse.ok) {
          const existingPaymentSessionId = existingParsed?.payment_session_id;
          if (existingPaymentSessionId) {
            return res.json({
              success: true,
              payment_session_id: existingPaymentSessionId,
              order_id,
              cf_order_id: existingParsed?.cf_order_id || null,
              reused: true,
            });
          }

          const existingStatus = String(existingParsed?.order_status || '').toUpperCase();
          if (existingStatus === 'PAID' || existingStatus === 'SUCCESS') {
            return res.status(409).json({
              success: false,
              error: 'cashfree order already paid',
              code: 'order_already_paid',
              order_id,
              details: existingParsed,
            });
          }
        }
      }

      console.error('Cashfree create-order failed:', cashfreeResponse.status, raw);
      return res.status(cashfreeResponse.status).json({
        success: false,
        error: 'cashfree create-order failed',
        details: parsed || raw,
      });
    }

    const paymentSessionId = parsed?.payment_session_id;
    if (!paymentSessionId) {
      return res.status(502).json({
        success: false,
        error: 'missing payment_session_id from cashfree',
        details: parsed || raw,
      });
    }

    return res.json({
      success: true,
      payment_session_id: paymentSessionId,
      order_id,
      cf_order_id: parsed?.cf_order_id || null,
    });
  } catch (err) {
    console.error('Unexpected /payment/create-order error:', err);
    return res.status(500).json({ success: false });
  }
});

app.post('/activate', async (req, res) => {
  try {
    const { device_id, order_id, amount, plan_code } = req.body || {};

    if (!device_id || !order_id) {
      return res.status(400).json({ success: false, error: 'device_id and order_id are required' });
    }

    const { subscription, error: subscriptionError } = await getOrCreateSubscriptionByDevice(device_id);

    if (subscriptionError || !subscription) {
      console.error('Failed to resolve subscription for activation:', subscriptionError);
      return res.status(500).json({ success: false });
    }

    const selectedPlan = resolvePlan(plan_code, amount);
    const expiry = getExpiryForPlan(new Date(), selectedPlan).toISOString();
    const resolvedAmount = selectedPlan.amount;

    const { error: activateError } = await setSubscriptionSuccess(
      subscription.id,
      order_id,
      resolvedAmount,
      expiry
    );

    if (activateError) {
      console.error('Failed to activate subscription:', activateError);
      return res.status(500).json({ success: false });
    }

    await supabase
      .from('deposits')
      .update({ status: 'success', UTR: DEPOSIT_UTR_PLACEHOLDER })
      .eq('order_id', order_id)
      .eq('user_id', subscription.id);

    return res.json({
      success: true,
      subscription_uuid: subscription.id,
      plan_code: selectedPlan.code,
      plan_validity: selectedPlan.label,
    });
  } catch (err) {
    console.error('Unexpected /activate error:', err);
    return res.status(500).json({ success: false });
  }
});

const PORT = Number(process.env.PORT) || 3000;

app.listen(PORT, () => {
  console.log(`Backend running on port ${PORT}`);
});
