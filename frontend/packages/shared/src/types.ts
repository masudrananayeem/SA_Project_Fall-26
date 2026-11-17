/** JSON shapes returned by the Spring Boot backend (BigDecimal values arrive as numbers). */
export type Num = number | string;

export interface Plan {
  id: string;
  planKey: string;
  version: number;
  name: string;
  currency: string;
  flatFee: Num;
  unitPrice: Num;
  metric: string;
  freeAllowance: Num;
  minimumCommitment: Num;
  active: boolean;
  chargeDefinitionsJson: string | null;
  taxRate: Num | null;
  aggregation: string | null;
  aggregationProperty: string | null;
  metricFiltersJson: string | null;
}

export interface Tier {
  upTo: Num | null;
  unitPrice: Num;
}

export interface ChargeDefinition {
  type: string;
  name: string;
  amount: Num;
  includedUnits?: Num;
  packageSize?: Num;
  tiers?: Tier[];
}

export interface Subscription {
  id: string;
  customerId: string;
  planId: string;
  status: string;
  periodStart: string;
  periodEnd: string;
  seatCount: number;
  trialEnd: string | null;
  trialStatus: string | null;
  trialAutoConvert: boolean;
  scheduledPlanId: string | null;
  scheduledChangeDate: string | null;
  trialing: boolean;
}

export interface PlanChange {
  id: string;
  subscriptionId: string;
  fromPlanId: string;
  toPlanId: string;
  proratedDelta: Num;
  currency: string;
  effectiveDate: string;
  kind: string;
  explanation: string | null;
  invoiceId: string | null;
}

export interface Invoice {
  id: string;
  customerId: string;
  period: string;
  status: string;
  currency: string;
  subtotal: Num;
  tax: Num;
  creditApplied: Num | null;
  total: Num;
  taxRule: string | null;
  planId: string | null;
  billedQuantity: Num | null;
  seatCount: number | null;
  createdAt: string;
  finalisedAt: string | null;
  paidAt: string | null;
}

export interface InvoiceLine {
  id: string;
  invoiceId: string;
  kind: string;
  lineName: string | null;
  reference: string | null;
  metric: string | null;
  quantity: Num;
  unitPrice: Num;
  amount: Num;
  priceRule: string | null;
  taxRule: string | null;
  sourceEventCount: number;
}

export interface LineTrace {
  invoiceId: string;
  customerId: string;
  period: string;
  lineId: string;
  kind: string;
  line: string;
  metric: string | null;
  quantity: Num;
  amount: Num;
  priceRule: string | null;
  taxRule: string | null;
  eventCount: number;
  eventsFound: number;
  aggregation: string;
  billedUsageQuantity: Num | null;
  recomputedQuantity: Num | null;
  quantityMatches: boolean | null;
  eventIds: string[];
  eventIdsTruncated: boolean;
}

export interface EstimateLine {
  name: string;
  type: string;
  quantity: Num | null;
  amount: Num;
  rule: string;
}

export interface Estimate {
  customerId: string;
  period: string;
  asOf: string;
  refreshSeconds: number;
  currency: string;
  estimatedAmount: Num;
  note?: string;
  plan?: string;
  billableMetric?: string;
  aggregation?: string;
  billableQuantity?: Num;
  eventCount?: number;
  lines?: EstimateLine[];
  estimatedSubtotal?: Num;
  taxRate?: Num;
  estimatedTax?: Num;
}

export interface UsageSummary {
  customerId: string;
  eventCount: number;
  usage: Record<string, Num>;
  periodStart: string;
  asOf: string;
}

export interface Aggregation {
  customerId: string;
  metric: string;
  aggregation: string;
  value: Num;
  matchedEventCount: number;
  from: string;
  to: string;
  filters: Record<string, string>;
}

export interface IngestResult {
  received: number;
  accepted: number;
  duplicates: number;
  lateAccepted: number;
  rejected: number;
  rejectionDetails: { idempotencyKey: string; reason: string }[];
}

export interface UsageEventInput {
  customerId: string;
  metric: string;
  quantity: number;
  timestamp: string;
  idempotencyKey: string;
  properties?: Record<string, unknown>;
}

export interface BillingLinePreview {
  kind: string;
  description: string;
  amount: Num;
  rule: string;
  quantity?: Num;
  sourceEventCount?: number;
}

export interface BillingOutcome {
  customerId: string;
  period: string;
  status: 'CREATED' | 'EXISTING' | 'SKIPPED' | 'SIMULATED' | string;
  created: number;
  reason?: string;
  invoiceId?: string;
  plan?: string;
  currency?: string;
  billedQuantity?: Num;
  lines?: BillingLinePreview[];
  subtotal?: Num;
  tax?: Num;
  taxRule?: string;
  creditApplied?: Num;
  total?: Num;
  simulationOnly?: boolean;
  planId?: string;
  planVersion?: number;
  current?: boolean;
}

export interface BillingRunSummary {
  period: string;
  created: number;
  alreadyExisted: number;
  skipped: number;
  status: string;
  failed: string[];
}

export interface VersionComparison {
  customerId: string;
  planKey: string;
  simulations: BillingOutcome[];
}

export interface BatchRunResult {
  jobName: string;
  period: string;
  executionId?: number;
  status: string;
  exitStatus?: string;
  message: string;
}

export interface MigrationResult {
  planKey: string;
  fromVersion: number;
  toVersion: number;
  dryRun: boolean;
  affectedSubscriptions: number;
  impact: { subscriptionId: string; customerId: string; oldRecurring: Num; newRecurring: Num; prorationDelta: Num }[];
}

export interface DunningCase {
  id: string;
  invoiceId: string;
  customerId: string;
  status: string;
  attempt: number;
  nextAttemptAt: string | null;
  createdAt: string;
  pastDueAt: string | null;
  suspendedAt: string | null;
  resolvedAt: string | null;
}

export interface PaymentResult {
  invoiceId: string;
  paymentStatus: string;
  reference?: string;
  reason?: string;
  dunningCaseId?: string;
  nextAttemptAt?: string;
  provider?: string;
}

export interface Webhook {
  id: string;
  customerId: string;
  endpoint: string;
  active: boolean;
}

export interface WebhookDelivery {
  id: string;
  webhookId: string;
  eventType: string;
  payload: string;
  status: string;
  attempts: number;
  lastResponse: string | null;
  nextAttemptAt: string | null;
}

export interface CustomerNotification {
  id: string;
  customerId: string;
  eventType: string;
  message: string;
  createdAt: string;
}

export interface CreditAccount {
  id: string | null;
  customerId: string;
  balance: Num;
  currency: string;
}

export interface CreditNote {
  id: string;
  customerId: string;
  amount: Num;
  currency: string;
  reason: string;
  createdAt: string;
}

export interface BudgetEvaluation {
  status?: 'NO_BUDGET' | string;
  customerId?: string;
  period?: string;
  budget?: Num;
  currency?: string;
  estimatedAmount?: Num;
  percentUsed?: Num;
  notifiedThresholds?: string | null;
  newAlerts?: unknown[];
}

export interface LateAdjustment {
  id: string;
  usageEventId: string;
  customerId: string;
  billingPeriod: string;
  status: string;
  createdAt: string;
  invoiceId: string | null;
  resolvedAt: string | null;
}
