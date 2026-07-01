import { GuaraniPipe } from './guarani.pipe';
import { GuaraniCortoPipe } from './guarani-corto.pipe';
import { VariacionPipe } from './variacion.pipe';

describe('GuaraniPipe', () => {
  const pipe = new GuaraniPipe();

  it('should format positive numbers', () => {
    expect(pipe.transform(487015510)).toBe('Gs. 487.015.510');
  });

  it('should format zero', () => {
    expect(pipe.transform(0)).toBe('Gs. 0');
  });

  it('should format negative numbers', () => {
    expect(pipe.transform(-3100000)).toBe('Gs. -3.100.000');
  });

  it('should handle null', () => {
    expect(pipe.transform(null)).toBe('Gs. 0');
  });
});

describe('GuaraniCortoPipe', () => {
  const pipe = new GuaraniCortoPipe();

  it('should abbreviate millions', () => {
    expect(pipe.transform(487015510)).toBe('Gs. 487.0M');
  });

  it('should abbreviate billions', () => {
    expect(pipe.transform(1500000000)).toBe('Gs. 1.500.0M');
  });

  it('should show full number under 1M', () => {
    expect(pipe.transform(500000)).toBe('Gs. 500.000');
  });
});

describe('VariacionPipe', () => {
  const pipe = new VariacionPipe();

  it('should format positive variation', () => {
    expect(pipe.transform(0.098)).toBe('▲ +9.8%');
  });

  it('should format negative variation', () => {
    expect(pipe.transform(-0.032)).toBe('▼ -3.2%');
  });

  it('should format zero', () => {
    expect(pipe.transform(0)).toBe('— 0.0%');
  });

  it('should handle null', () => {
    expect(pipe.transform(null)).toBe('—');
  });
});
