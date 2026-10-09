"use client";

import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartTooltip } from "@/components/charts/ChartTooltip";

export function TrendAreaChart<T extends object>({
  data,
  dataKey,
  xKey,
  height = 220,
  colorVar = "--violet",
  domain = [0, 100],
}: {
  data: T[];
  dataKey: keyof T & string;
  xKey: keyof T & string;
  height?: number;
  colorVar?: string;
  domain?: [number | "auto", number | "auto"];
}) {
  const gradId = `trendGradient_${dataKey}`;
  return (
    <ResponsiveContainer width="100%" height={height}>
      <AreaChart data={data as Record<string, unknown>[]} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <defs>
          <linearGradient id={gradId} x1="0" y1="0" x2="0" y2="1">
            <stop offset="5%" stopColor={`hsl(var(${colorVar}))`} stopOpacity={0.45} />
            <stop offset="95%" stopColor={`hsl(var(${colorVar}))`} stopOpacity={0} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
        <XAxis
          dataKey={String(xKey)}
          tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
          axisLine={{ stroke: "hsl(var(--border))" }}
          tickLine={false}
        />
        <YAxis
          domain={domain}
          tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={32}
        />
        <Tooltip content={<ChartTooltip />} cursor={{ stroke: "hsl(var(--border))" }} />
        <Area
          type="monotone"
          dataKey={String(dataKey)}
          stroke={`hsl(var(${colorVar}))`}
          strokeWidth={2.5}
          fill={`url(#${gradId})`}
          dot={{ r: 3, fill: `hsl(var(${colorVar}))`, strokeWidth: 0 }}
          activeDot={{ r: 5 }}
        />
      </AreaChart>
    </ResponsiveContainer>
  );
}
