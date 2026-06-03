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

const PLAN_DAILY = {
  code: 'daily',
  amount: 50,
  durationMs: 24 * 60 * 60 * 1000,
  label: '1 Day',
};
const PLAN_MONTHLY = {
  code: 'monthly',
  amount: 1200,
  durationMs: 30 * 24 * 60 * 60 * 1000,
  label: '30 Days',
};
const DEFAULT_PLAN = PLAN_DAILY;
const PLAN_AMOUNT = DEFAULT_PLAN.amount;
const DEPOSIT_UTR_PLACEHOLDER = '00000000000';
const CASHFREE_API_BASE = process.env.CASHFREE_API_BASE || 'https://api.cashfree.com';
const CASHFREE_API_VERSION = process.env.CASHFREE_API_VERSION || '2023-08-01';
const CASHFREE_MODE = process.env.CASHFREE_MODE || 'production';
const CASHFREE_APP_ID = process.env.CASHFREE_APP_ID || '';
const CASHFREE_SECRET_KEY = process.env.CASHFREE_SECRET_KEY || '';
const CASHFREE_DEFAULT_PHONE = process.env.CASHFREE_DEFAULT_PHONE || '9999999999';
const PUBLIC_BASE_URL = (process.env.PUBLIC_BASE_URL || 'https://arbsmartbot-b6rn.onrender.com').replace(/\/+$/, '');
const APP_RETURN_URL = process.env.APP_RETURN_URL || 'myapp://payment-success';

const supabase = createClient(SUPABASE_URL, SUPABASE_KEY);

const addMs = (date, ms) => new Date(new Date(date).getTime() + ms);
const PLAN_CODE_ALIASES = {
  daily: PLAN_DAILY.code,
  day: PLAN_DAILY.code,
  '1d': PLAN_DAILY.code,
  pro: PLAN_DAILY.code,
  monthly: PLAN_MONTHLY.code,
  month: PLAN_MONTHLY.code,
  '30d': PLAN_MONTHLY.code,
};
const PLAN_BY_CODE = {
  [PLAN_DAILY.code]: PLAN_DAILY,
  [PLAN_MONTHLY.code]: PLAN_MONTHLY,
};
const ALL_PLANS = [PLAN_DAILY, PLAN_MONTHLY];

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
const isActiveSubscriptionStatus = (value) => {
  const status = String(value || '').trim().toLowerCase();
  return status === 'success' || status === 'active' || status === 'paid';
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

const escapeHtml = (value) =>
  String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const buildSuccessUrl = (orderId, subscriptionId) =>
  `${PUBLIC_BASE_URL}/payment/success?order_id=${encodeURIComponent(orderId)}&uuid=${encodeURIComponent(subscriptionId)}`;

const buildCheckoutUrl = (paymentSessionId, orderId) =>
  `${PUBLIC_BASE_URL}/payment/checkout?payment_session_id=${encodeURIComponent(paymentSessionId)}&order_id=${encodeURIComponent(orderId)}`;

async function syncPaidCashfreeOrder(orderId, subscriptionId) {
  if (!orderId || !subscriptionId || !CASHFREE_APP_ID || !CASHFREE_SECRET_KEY) {
    return { activated: false, error: 'missing order, subscription, or Cashfree credentials' };
  }

  const cashfreeResponse = await fetch(`${CASHFREE_API_BASE}/pg/orders/${encodeURIComponent(orderId)}`, {
    method: 'GET',
    headers: cashfreeHeaders(),
  });

  const raw = await cashfreeResponse.text();
  const parsed = parseJsonSafe(raw);

  if (!cashfreeResponse.ok) {
    console.error('Cashfree order lookup failed:', cashfreeResponse.status, raw);
    return { activated: false, error: 'cashfree order lookup failed' };
  }

  const status = String(parsed?.order_status || '').toUpperCase();
  if (status !== 'PAID' && status !== 'SUCCESS') {
    return { activated: false, error: `cashfree order is ${status || 'not paid'}` };
  }

  const { data: deposit, error: depositError } = await supabase
    .from('deposits')
    .select('order_id, amount, status, user_id')
    .eq('order_id', orderId)
    .eq('user_id', subscriptionId)
    .limit(1)
    .maybeSingle();

  if (depositError) {
    console.error('Failed to load deposit for paid order:', depositError);
    return { activated: false, error: 'deposit lookup failed' };
  }

  const paidAmount = Number(deposit?.amount ?? parsed?.order_amount);
  const selectedPlan = resolvePlan(null, paidAmount);
  const expiry = getExpiryForPlan(new Date(), selectedPlan).toISOString();

  const { error: activateError } = await setSubscriptionSuccess(
    subscriptionId,
    orderId,
    selectedPlan.amount,
    expiry
  );

  if (activateError) {
    console.error('Failed to activate paid Cashfree order:', activateError);
    return { activated: false, error: 'subscription activation failed' };
  }

  await supabase
    .from('deposits')
    .update({ status: 'success', UTR: DEPOSIT_UTR_PLACEHOLDER, amount: selectedPlan.amount })
    .eq('order_id', orderId)
    .eq('user_id', subscriptionId);

  return {
    activated: true,
    plan_code: selectedPlan.code,
    plan_validity: selectedPlan.label,
    expiry,
  };
}

async function createCashfreeOrder({ orderId, subscriptionId, amount, phone }) {
  if (!CASHFREE_APP_ID || !CASHFREE_SECRET_KEY) {
    return {
      ok: false,
      status: 500,
      body: {
        success: false,
        error: 'Cashfree credentials missing on backend',
      },
    };
  }

  const cashfreePayload = {
    order_id: orderId,
    order_amount: amount,
    order_currency: 'INR',
    customer_details: {
      customer_id: sanitizeCustomerId(subscriptionId),
      customer_phone: sanitizePhone(phone),
      customer_name: 'ARB User',
    },
    order_meta: {
      return_url: buildSuccessUrl(orderId, subscriptionId),
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
        `${CASHFREE_API_BASE}/pg/orders/${encodeURIComponent(orderId)}`,
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
          return {
            ok: true,
            body: {
              success: true,
              payment_session_id: existingPaymentSessionId,
              checkout_url: buildCheckoutUrl(existingPaymentSessionId, orderId),
              order_id: orderId,
              cf_order_id: existingParsed?.cf_order_id || null,
              reused: true,
            },
          };
        }

        const existingStatus = String(existingParsed?.order_status || '').toUpperCase();
        if (existingStatus === 'PAID' || existingStatus === 'SUCCESS') {
          return {
            ok: false,
            status: 409,
            body: {
              success: false,
              error: 'cashfree order already paid',
              code: 'order_already_paid',
              order_id: orderId,
              details: existingParsed,
            },
          };
        }
      }
    }

    console.error('Cashfree create-order failed:', cashfreeResponse.status, raw);
    return {
      ok: false,
      status: cashfreeResponse.status,
      body: {
        success: false,
        error: 'cashfree create-order failed',
        details: parsed || raw,
      },
    };
  }

  const paymentSessionId = parsed?.payment_session_id;
  if (!paymentSessionId) {
    return {
      ok: false,
      status: 502,
      body: {
        success: false,
        error: 'missing payment_session_id from cashfree',
        details: parsed || raw,
      },
    };
  }

  return {
    ok: true,
    body: {
      success: true,
      payment_session_id: paymentSessionId,
      checkout_url: buildCheckoutUrl(paymentSessionId, orderId),
      order_id: orderId,
      cf_order_id: parsed?.cf_order_id || null,
    },
  };
}

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

async function getSubscriptionByUuid(subscriptionUuid) {
  if (!subscriptionUuid || typeof subscriptionUuid !== 'string') {
    return { subscription: null, error: null };
  }

  const { data, error } = await supabase
    .from('subscriptions')
    .select('id, device_id, status, expiry, order_id, amount')
    .eq('id', subscriptionUuid)
    .limit(1)
    .maybeSingle();

  return { subscription: data || null, error };
}

async function bindSubscriptionToDevice(subscriptionId, deviceId) {
  if (!subscriptionId || !deviceId) return { error: null };
  const { error } = await supabase
    .from('subscriptions')
    .update({ device_id: deviceId })
    .eq('id', subscriptionId);
  return { error };
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

async function setSubscriptionInactive(subscriptionId) {
  if (!subscriptionId) return { error: null };
  const { error } = await supabase
    .from('subscriptions')
    .update({ status: 'inactive' })
    .eq('id', subscriptionId);
  return { error };
}

app.get('/check', async (req, res) => {
  try {
    const { device_id, subscription_uuid } = req.query;

    if (!device_id || typeof device_id !== 'string') {
      return res.status(400).json({ active: false, error: 'device_id is required' });
    }

    let { subscription, error } =
      typeof subscription_uuid === 'string'
        ? await getSubscriptionByUuid(subscription_uuid)
        : { subscription: null, error: null };

    if (error) {
      console.error('Failed to resolve subscription by UUID:', error);
    }

    if (subscription && subscription.device_id !== device_id) {
      const { error: bindError } = await bindSubscriptionToDevice(subscription.id, device_id);
      if (bindError) console.error('Failed to bind subscription to current device:', bindError);
      else subscription.device_id = device_id;
    }

    if (!subscription) {
      const resolved = await getOrCreateSubscriptionByDevice(device_id);
      subscription = resolved.subscription;
      error = resolved.error;
    }

    if (error || !subscription) {
      console.error('Failed to resolve subscription row:', error);
      return res.status(500).json({ active: false });
    }

    const subscriptionExpiry = parseDateValue(subscription.expiry);
    let lastKnownExpiry = subscriptionExpiry;
    if (isActiveSubscriptionStatus(subscription.status) && isFutureDate(subscriptionExpiry)) {
      return res.json({
        active: true,
        source: 'subscriptions',
        subscription_uuid: subscription.id,
        expiry: subscriptionExpiry ? subscriptionExpiry.toISOString() : null,
        expiry_raw: subscription.expiry ?? null,
        remaining_seconds: remainingSeconds(subscriptionExpiry),
      });
    }

    if (isActiveSubscriptionStatus(subscription.status) && isValidDate(subscription.expiry)) {
      const { error: inactiveError } = await setSubscriptionInactive(subscription.id);
      if (inactiveError) console.error('Failed to mark expired subscription inactive:', inactiveError);

      return res.json({
        active: false,
        source: 'subscriptions_expired',
        subscription_uuid: subscription.id,
        expiry: subscriptionExpiry ? subscriptionExpiry.toISOString() : null,
        expiry_raw: subscription.expiry ?? null,
        remaining_seconds: 0,
      });
    }

    const { deposit, error: depositError } = await getLatestActivationDeposit(subscription.id);

    if (depositError) {
      console.error('Failed to check deposits fallback:', depositError);
      return res.json({
        active: false,
        subscription_uuid: subscription.id,
        expiry: lastKnownExpiry ? lastKnownExpiry.toISOString() : null,
        expiry_raw: subscription.expiry ?? null,
        remaining_seconds: lastKnownExpiry ? remainingSeconds(lastKnownExpiry) : 0,
        fallback_error: 'deposit lookup failed',
      });
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
      await setSubscriptionInactive(subscription.id);
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

app.get('/', (_req, res) => {
  res.json({ ok: true, service: 'arb-smart-bot-backend' });
});

app.get('/payment/checkout', (req, res) => {
  const paymentSessionId = typeof req.query.payment_session_id === 'string' ? req.query.payment_session_id : '';
  const orderId = typeof req.query.order_id === 'string' ? req.query.order_id : '';

  if (!paymentSessionId) {
    return res.status(400).send('Missing payment session.');
  }

  const safeSessionId = escapeHtml(paymentSessionId);
  const safeOrderId = escapeHtml(orderId);
  const safeMode = escapeHtml(CASHFREE_MODE);

  return res
    .type('html')
    .send(`<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Cashfree Checkout</title>
    <script src="https://sdk.cashfree.com/js/v3/cashfree.js"></script>
    <style>
      body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #050806; color: #effff6; font-family: Arial, sans-serif; }
      main { width: min(420px, calc(100vw - 32px)); border: 1px solid #1f4f39; border-radius: 14px; padding: 22px; background: #0d1410; text-align: center; }
      h1 { margin: 0 0 8px; font-size: 22px; }
      p { color: #a9c5b7; font-size: 14px; line-height: 1.45; }
      button { border: 0; border-radius: 10px; padding: 12px 16px; background: #00ff99; color: #03120b; font-weight: 800; width: 100%; }
      .muted { font-size: 12px; color: #769184; overflow-wrap: anywhere; }
    </style>
  </head>
  <body>
    <main>
      <h1>Opening Cashfree</h1>
      <p>Please wait while Cashfree checkout opens.</p>
      <button id="payBtn" type="button">Open Cashfree Checkout</button>
      <p class="muted">Order: ${safeOrderId}</p>
    </main>
    <script>
      const cashfree = Cashfree({ mode: "${safeMode}" });
      const openCheckout = () => cashfree.checkout({
        paymentSessionId: "${safeSessionId}",
        redirectTarget: "_self"
      });
      document.getElementById("payBtn").addEventListener("click", openCheckout);
      setTimeout(openCheckout, 250);
    </script>
  </body>
</html>`);
});

app.get('/payment/success', async (req, res) => {
  const orderId = typeof req.query.order_id === 'string' ? req.query.order_id : '';
  const uuid = typeof req.query.uuid === 'string' ? req.query.uuid : '';
  let sync = { activated: false };
  try {
    sync = await syncPaidCashfreeOrder(orderId, uuid);
  } catch (err) {
    console.error('Payment success sync failed:', err);
  }
  const deepLink =
    `${APP_RETURN_URL}?order_id=${encodeURIComponent(orderId)}&uuid=${encodeURIComponent(uuid)}&activated=${sync.activated ? '1' : '0'}`;

  return res
    .type('html')
    .send(`<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Payment Complete</title>
    <style>
      body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #050806; color: #effff6; font-family: Arial, sans-serif; }
      main { width: min(420px, calc(100vw - 32px)); border: 1px solid #1f4f39; border-radius: 14px; padding: 22px; background: #0d1410; text-align: center; }
      a { display: block; border-radius: 10px; padding: 12px 16px; background: #00ff99; color: #03120b; font-weight: 800; text-decoration: none; }
      p { color: #a9c5b7; font-size: 14px; line-height: 1.45; }
    </style>
  </head>
  <body>
    <main>
      <h1>Payment Complete</h1>
      <p>${sync.activated ? 'Your subscription is active. Return to ARB Smart Bot.' : 'Return to ARB Smart Bot to verify your subscription.'}</p>
      <a href="${escapeHtml(deepLink)}">Open App</a>
    </main>
    <script>window.location.href = ${JSON.stringify(deepLink)};</script>
  </body>
</html>`);
});

app.post('/payment/init', async (req, res) => {
  try {
    const { device_id, amount, plan_code, phone } = req.body || {};

    if (!device_id || typeof device_id !== 'string') {
      return res.status(400).json({ success: false, error: 'device_id is required' });
    }

    const selectedPlan = resolvePlan(plan_code, amount);
    const paymentAmount = selectedPlan.amount;

    if (!CASHFREE_APP_ID || !CASHFREE_SECRET_KEY) {
      return res.status(500).json({
        success: false,
        error: 'Cashfree credentials missing on backend',
      });
    }

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

    const cashfreeOrder = await createCashfreeOrder({
      orderId,
      subscriptionId: subscription.id,
      amount: paymentAmount,
      phone,
    });

    if (!cashfreeOrder.ok) {
      return res.status(cashfreeOrder.status || 500).json(cashfreeOrder.body);
    }

    return res.json({
      success: true,
      payment_url: cashfreeOrder.body.checkout_url,
      checkout_url: cashfreeOrder.body.checkout_url,
      payment_session_id: cashfreeOrder.body.payment_session_id,
      order_id: orderId,
      cf_order_id: cashfreeOrder.body.cf_order_id || null,
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
    const { order_id, user_id, amount, phone } = req.body || {};

    if (!order_id || !user_id) {
      return res.status(400).json({ success: false, error: 'order_id and user_id are required' });
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

    const cashfreeOrder = await createCashfreeOrder({
      orderId: order_id,
      subscriptionId: user_id,
      amount: resolvedAmount,
      phone,
    });

    if (!cashfreeOrder.ok) {
      return res.status(cashfreeOrder.status || 500).json(cashfreeOrder.body);
    }

    return res.json(cashfreeOrder.body);
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
