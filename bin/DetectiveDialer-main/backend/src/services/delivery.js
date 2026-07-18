// Delivery coordination helpers for common Indian delivery services.
// When the AI classifies a call as DELIVERY, these scripts shape the response
// so the agent can hand over the address / gate code without bothering the owner.

export const DELIVERY_SERVICES = [
  'blinkit',
  'zepto',
  'swiggy',
  'instamart',
  'zomato',
  'amazon',
  'flipkart',
  'delhivery',
  'porter',
  'dunzo',
  'bigbasket',
  'ecom express',
  'bluedart',
  'dtdc',
  'shadowfax',
];

export function mentionsDelivery(text) {
  if (!text) return false;
  const lower = text.toLowerCase();
  return DELIVERY_SERVICES.some((s) => lower.includes(s)) ||
    /\b(delivery|deliver|courier|parcel|package|order id|order number|rider|driver)\b/.test(lower);
}

// Build a concrete instruction for the AI to relay, based on the service.
export function deliveryScript({ text = '', address = '', gateCode = '' }) {
  const lower = text.toLowerCase();
  const addr = address || 'the saved home address';
  const gate = gateCode ? ` The gate code is ${gateCode}.` : '';

  if (/(blinkit|zepto|instamart|dunzo|bigbasket)/.test(lower)) {
    return `Give the delivery address: ${addr}.${gate} Tell them to leave it at the door if no one answers.`;
  }
  if (/(amazon|flipkart|ecom|bluedart|dtdc|delhivery|shadowfax)/.test(lower)) {
    return `Confirm acceptance of the package. Provide the address: ${addr}.${gate} If a delivery OTP is needed, tell them the owner will share it shortly.`;
  }
  if (/(porter|dunzo)/.test(lower)) {
    return `This may be a pickup. Confirm the pickup address: ${addr}.${gate} Ask what needs to be picked up.`;
  }
  if (/(swiggy|zomato)/.test(lower)) {
    return `Food delivery. Give the address: ${addr}.${gate} Confirm the order will be received.`;
  }
  return `Generic delivery. Ask for the order ID, then give the address: ${addr}.${gate}`;
}

export default { DELIVERY_SERVICES, mentionsDelivery, deliveryScript };
