import {
  Component,
  HostListener,
  NgZone,
  OnDestroy,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NavigationEnd, Router } from '@angular/router';
import { Subscription, filter } from 'rxjs';
import { LucideAngularModule, ArrowLeft, ArrowRight, Check, X } from 'lucide-angular';
import { OnboardingService } from '../../services/onboarding.service';

interface Box {
  top: number;
  left: number;
  width: number;
  height: number;
}

@Component({
  selector: 'app-onboarding',
  standalone: true,
  imports: [LucideAngularModule],
  templateUrl: './onboarding.component.html',
})
export class OnboardingComponent implements OnInit, OnDestroy {
  readonly onboarding = inject(OnboardingService);
  private router = inject(Router);
  private zone = inject(NgZone);

  readonly icons = { ArrowLeft, ArrowRight, Check, X };

  /** Bounding box of the highlighted target, in viewport coords. Null if not found. */
  readonly targetBox = signal<Box | null>(null);
  /** Number of times we re-tried locating the target for the current step. */
  private retryCount = 0;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;
  private routerSub?: Subscription;

  readonly stepNumber = computed(() => this.onboarding.currentIndex() + 1);
  readonly stepTotal = computed(() => this.onboarding.total());
  readonly progressPct = computed(() =>
    Math.round((this.stepNumber() / this.stepTotal()) * 100),
  );

  /** Tooltip position derived from targetBox. Falls back to viewport center. */
  readonly tooltipStyle = computed<Record<string, string>>(() => {
    const box = this.targetBox();
    if (!box) {
      return {
        top: '50%',
        left: '50%',
        transform: 'translate(-50%, -50%)',
      };
    }
    const tooltipW = 360;
    const tooltipH = 200;
    const margin = 12;
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // Prefer below the target; if it doesn't fit, place above; else, beside.
    let top = box.top + box.height + margin;
    let left = box.left + box.width / 2 - tooltipW / 2;

    if (top + tooltipH > vh - 8) {
      top = box.top - tooltipH - margin;
    }
    if (top < 8) {
      // Center vertically next to it
      top = Math.max(8, box.top + box.height / 2 - tooltipH / 2);
      left = box.left + box.width + margin;
      if (left + tooltipW > vw - 8) {
        left = Math.max(8, box.left - tooltipW - margin);
      }
    }

    left = Math.max(8, Math.min(left, vw - tooltipW - 8));
    top = Math.max(8, Math.min(top, vh - tooltipH - 8));

    return {
      top: `${top}px`,
      left: `${left}px`,
      transform: 'none',
    };
  });

  /** Highlight box style (the bright outline around the target). */
  readonly highlightStyle = computed<Record<string, string>>(() => {
    const box = this.targetBox();
    if (!box) {
      return { display: 'none', top: '0', left: '0', width: '0', height: '0' };
    }
    return {
      display: 'block',
      top: `${box.top - 6}px`,
      left: `${box.left - 6}px`,
      width: `${box.width + 12}px`,
      height: `${box.height + 12}px`,
    };
  });

  // When current step changes, locate the target.
  private locateEffect = effect(() => {
    const step = this.onboarding.currentStep();
    if (step) {
      this.retryCount = 0;
      // Run locateTarget outside the change detection cycle.
      queueMicrotask(() => this.locateTarget());
    } else {
      this.targetBox.set(null);
    }
  });

  ngOnInit(): void {
    // Re-locate the target after every route change (since steps may navigate).
    this.routerSub = this.router.events
      .pipe(filter((e) => e instanceof NavigationEnd))
      .subscribe(() => {
        if (this.onboarding.isActive()) {
          // Wait for the new view to render
          this.zone.runOutsideAngular(() => {
            setTimeout(() => this.zone.run(() => this.locateTarget()), 80);
          });
        }
      });
  }

  ngOnDestroy(): void {
    this.routerSub?.unsubscribe();
    if (this.retryTimer) clearTimeout(this.retryTimer);
  }

  @HostListener('window:resize')
  @HostListener('window:scroll')
  onWindowChange(): void {
    if (this.onboarding.isActive()) {
      this.locateTarget();
    }
  }

  private locateTarget(): void {
    const step = this.onboarding.currentStep();
    if (!step) {
      this.targetBox.set(null);
      return;
    }
    const el = document.querySelector(step.selector) as HTMLElement | null;
    if (!el) {
      // Retry a few times (target may render after route navigation).
      if (this.retryCount < 10) {
        this.retryCount++;
        if (this.retryTimer) clearTimeout(this.retryTimer);
        this.retryTimer = setTimeout(() => this.locateTarget(), 200);
      } else {
        // Give up locating — show tooltip centered without highlight.
        this.targetBox.set(null);
      }
      return;
    }
    const rect = el.getBoundingClientRect();
    this.targetBox.set({
      top: rect.top,
      left: rect.left,
      width: rect.width,
      height: rect.height,
    });
    // Scroll into view if needed
    if (rect.top < 0 || rect.bottom > window.innerHeight) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }

  next(): void {
    this.onboarding.next();
  }

  prev(): void {
    this.onboarding.prev();
  }

  skip(): void {
    this.onboarding.skip();
  }
}
