"use client";

import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartTooltip } from "@/components/charts/ChartTooltip";

export function VerticalBarChart<T extends object>({
  data,
  xKey,
  yKey,
  height = 240,
  colorVar = "--violet",
}: {
  data: T[];
  xKey: keyof T & string;
  yKey: keyof T & string;
  height?: number;
  colorVar?: string;
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data as Record<string, unknown>[]} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
        <XAxis
          dataKey={String(xKey)}
          tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
          axisLine={{ stroke: "hsl(var(--border))" }}
          tickLine={false}
        />
        <YAxis tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }} axisLine={false} tickLine={false} width={40} />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: "hsl(var(--accent) / 0.4)" }} />
        <Bar dataKey={String(yKey)} radius={[6, 6, 0, 0]} fill={`hsl(var(${colorVar}))`} maxBarSize={40} />
      </BarChart>
    </ResponsiveContainer>
  );
}
