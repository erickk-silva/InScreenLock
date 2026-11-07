export type UsageProjection = { annualHours: number; equivalentDays: number };

/** Projects a daily habit across a 365-day hypothetical year. */
export function calculateUsageProjection(minutesPerDay: number): UsageProjection {
  const safeMinutes = Math.min(1440, Math.max(0, Math.round(minutesPerDay)));
  return {
    annualHours: Math.round((safeMinutes * 365 / 60) * 10) / 10,
    equivalentDays: Math.round((safeMinutes * 365 / 1440) * 10) / 10,
  };
}

export function calculateLimitDemo(goalMinutes: number, usedMinutes: number) {
  const excessMinutes = Math.max(0, Math.round(usedMinutes) - Math.round(goalMinutes));
  return { exceeded: excessMinutes > 0, excessMinutes };
}
