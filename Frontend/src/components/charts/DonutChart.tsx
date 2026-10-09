"use client";

import { Cell, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import { ChartTooltip } from "@/components/charts/ChartTooltip";

export function DonutChart({
  data,
  colors,
  size = 180,
  centerLabel,
  centerValue,
}: {
  data: { name: string; value: number }[];
  colors: string[]; // hsl var refs like "--success"
  size?: number;
  centerLabel?: string;
  centerValue?: string | number;
}) {
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <ResponsiveContainer width={size} height={size}>
        <PieChart>
          <Pie
            data={data}
            dataKey="value"
            nameKey="name"
            innerRadius={size * 0.32}
            outerRadius={size * 0.46}
            paddingAngle={3}
            strokeWidth={0}
          >
            {data.map((_, i) => (
              <Cell key={i} fill={`hsl(var(${colors[i % colors.length]}))`} />
            ))}
          </Pie>
          <Tooltip content={<ChartTooltip />} />
        </PieChart>
      </ResponsiveContainer>
      {(centerLabel || centerValue !== undefined) && (
        <div className="pointer-events-none absolute flex flex-col items-center">
          {centerValue !== undefined && (
            <span className="text-xl font-semibold tabular-nums text-foreground">{centerValue}</span>
          )}
          {centerLabel && <span className="text-[10px] text-muted-foreground">{centerLabel}</span>}
        </div>
      )}
    </div>
  );
}
