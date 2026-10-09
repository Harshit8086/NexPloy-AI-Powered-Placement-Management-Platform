"use client";

import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { ChartTooltip } from "@/components/charts/ChartTooltip";

export function HorizontalBarChart<T extends object>({
  data,
  xKey,
  yKey,
  height = 260,
  colorVar = "--violet",
  colorByValue,
  domain = [0, 100],
}: {
  data: T[];
  xKey: keyof T & string;
  yKey: keyof T & string;
  height?: number;
  colorVar?: string;
  /** optional fn(value) => raw "H S% L%" triplet, overrides colorVar per bar */
  colorByValue?: (value: number) => string;
  domain?: [number | "auto", number | "auto"];
}) {
  return (
    <ResponsiveContainer width="100%" height={height}>
      <BarChart data={data as Record<string, unknown>[]} layout="vertical" margin={{ top: 4, right: 24, left: 8, bottom: 4 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" horizontal={false} />
        <XAxis
          type="number"
          domain={domain}
          tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          type="category"
          dataKey={String(yKey)}
          tick={{ fill: "hsl(var(--muted-foreground))", fontSize: 11 }}
          axisLine={false}
          tickLine={false}
          width={140}
        />
        <Tooltip content={<ChartTooltip />} cursor={{ fill: "hsl(var(--accent) / 0.4)" }} />
        <Bar dataKey={String(xKey)} radius={[0, 6, 6, 0]} maxBarSize={16}>
          {data.map((d, i) => (
            <Cell
              key={i}
              fill={
                colorByValue
                  ? `hsl(${colorByValue(Number((d as Record<string, unknown>)[xKey]))})`
                  : `hsl(var(${colorVar}))`
              }
            />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}
