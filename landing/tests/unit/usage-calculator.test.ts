import { describe, expect, it } from 'vitest';
import { calculateLimitDemo, calculateUsageProjection } from '@/lib/usage-calculator';

describe('usage projection', () => {
  it('projects 60 minutes per day as 365 hours and about 15.2 days', () => {
    expect(calculateUsageProjection(60)).toEqual({ annualHours: 365, equivalentDays: 15.2 });
  });

  it('rounds to one decimal and clamps the input to a day', () => {
    expect(calculateUsageProjection(0).equivalentDays).toBe(0);
    expect(calculateUsageProjection(1500)).toEqual({ annualHours: 8760, equivalentDays: 365 });
    expect(calculateUsageProjection(6)).toEqual({ annualHours: 36.5, equivalentDays: 1.5 });
  });
});

describe('limit scenario', () => {
  it('marks a 21 minute scenario as four minutes above a 17 minute goal', () => {
    expect(calculateLimitDemo(17, 21)).toEqual({ exceeded: true, excessMinutes: 4 });
  });

  it('does not show excess when usage is at or below the goal', () => {
    expect(calculateLimitDemo(17, 17)).toEqual({ exceeded: false, excessMinutes: 0 });
  });
});
