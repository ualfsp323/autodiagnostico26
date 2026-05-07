import { Component, OnInit, OnDestroy, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { VehicleApiService } from '../../services/vehicle-api.service';
import {
  VehicleModelSummary,
  VehicleVariant,
  VehicleSearchContext,
  EngineType,
  TransmissionType,
} from '../../services/api.models';

interface EnumOption<T> {
  value: T;
  label: string;
}

@Component({
  selector: 'app-buscar-vehiculo',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './buscar-vehiculo.html',
  styleUrl: './buscar-vehiculo.css',
})
export class BuscarVehiculo implements OnInit, OnDestroy {
  @Output() vehicleContextChange = new EventEmitter<VehicleSearchContext>();

  form!: FormGroup;

  brands: string[] = [];
  models: VehicleModelSummary[] = [];
  variants: VehicleVariant[] = [];

  loadingBrands = false;
  loadingModels = false;
  loadingVariants = false;

  readonly engineTypeOptions: EnumOption<EngineType>[] = [
    { value: 'PETROL', label: 'Gasolina' },
    { value: 'DIESEL', label: 'Diésel' },
    { value: 'BEV', label: 'Eléctrico (BEV)' },
    { value: 'HEV', label: 'Híbrido (HEV)' },
    { value: 'PHEV', label: 'Híbrido enchufable (PHEV)' },
    { value: 'REEV', label: 'Eléctrico con autonomía extendida (REEV)' },
  ];

  readonly transmissionOptions: EnumOption<TransmissionType>[] = [
    { value: 'MT', label: 'Manual (MT)' },
    { value: 'AT', label: 'Automático (AT)' },
    { value: 'CVT', label: 'Variador continuo (CVT)' },
    { value: 'iMT', label: 'Manual inteligente (iMT)' },
    { value: 'DCT', label: 'Doble embrague (DCT)' },
    { value: 'eCVT', label: 'Variador electrónico (eCVT)' },
    { value: 'DSG', label: 'DSG (VAG)' },
  ];

  readonly currentYear = new Date().getFullYear();

  private readonly precisionLabels = [
    'Sin información del vehículo',
    'Búsqueda amplia',
    'Búsqueda estándar',
    'Búsqueda detallada',
    'Máxima precisión',
  ];

  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private vehicleApi: VehicleApiService,
  ) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      brand: [null],
      modelId: [{ value: null, disabled: true }],
      variantId: [{ value: null, disabled: true }],
      year: [null],
      engineType: [null],
      transmission: [null],
    });

    this.loadBrands();
    this.listenBrandChanges();
    this.listenModelChanges();
    this.listenVariantChanges();
    this.listenAllChanges();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  // ── Getters ────────────────────────────────────────────────────────────────

  get precisionLevel(): number {
    const v = this.form.getRawValue();
    let score = 0;
    if (v.brand) score++;
    if (v.modelId) score++;
    if (v.variantId || v.year) score++;
    if (v.engineType || v.transmission) score++;
    return Math.min(score, 4);
  }

  get precisionLabel(): string {
    return this.precisionLabels[this.precisionLevel];
  }

  get precisionDots(): boolean[] {
    return [0, 1, 2, 3].map(i => i < this.precisionLevel);
  }

  // ── Private helpers ────────────────────────────────────────────────────────

  private loadBrands(): void {
    this.loadingBrands = true;
    this.vehicleApi
      .getBrands()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: brands => {
          this.brands = brands;
          this.loadingBrands = false;
        },
        error: () => {
          this.loadingBrands = false;
        },
      });
  }

  private listenBrandChanges(): void {
    this.form.get('brand')!.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(brand => {
      this.models = [];
      this.variants = [];
      this.form.get('modelId')!.setValue(null, { emitEvent: false });
      this.form.get('variantId')!.setValue(null, { emitEvent: false });
      this.form.get('modelId')!.disable({ emitEvent: false });
      this.form.get('variantId')!.disable({ emitEvent: false });

      if (brand) {
        this.loadingModels = true;
        this.vehicleApi
          .getModels(brand)
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: models => {
              this.models = models;
              this.form.get('modelId')!.enable({ emitEvent: false });
              this.loadingModels = false;
            },
            error: () => {
              this.loadingModels = false;
            },
          });
      }
    });
  }

  private listenModelChanges(): void {
    this.form.get('modelId')!.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(modelId => {
      this.variants = [];
      this.form.get('variantId')!.setValue(null, { emitEvent: false });
      this.form.get('variantId')!.disable({ emitEvent: false });

      if (modelId) {
        this.loadingVariants = true;
        this.vehicleApi
          .getVariants(Number(modelId))
          .pipe(takeUntil(this.destroy$))
          .subscribe({
            next: variants => {
              this.variants = variants;
              this.form.get('variantId')!.enable({ emitEvent: false });
              this.loadingVariants = false;
            },
            error: () => {
              this.loadingVariants = false;
            },
          });
      }
    });
  }

  private listenVariantChanges(): void {
    this.form.get('variantId')!.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(variantId => {
      if (variantId) {
        const variant = this.variants.find(v => v.id === Number(variantId));
        if (variant) {
          if (variant.engineType && !this.form.get('engineType')!.value) {
            this.form.get('engineType')!.setValue(variant.engineType, { emitEvent: false });
          }
          if (variant.transmission && !this.form.get('transmission')!.value) {
            this.form.get('transmission')!.setValue(variant.transmission, { emitEvent: false });
          }
        }
      }
    });
  }

  private listenAllChanges(): void {
    this.form.valueChanges.pipe(takeUntil(this.destroy$)).subscribe(() => {
      this.vehicleContextChange.emit(this.buildContext());
    });
  }

  private buildContext(): VehicleSearchContext {
    const raw = this.form.getRawValue();
    const modelId = raw.modelId ? Number(raw.modelId) : null;
    const modelName = this.models.find(m => m.id === modelId)?.name ?? null;
    const variantId = raw.variantId ? Number(raw.variantId) : null;
    const variantName = this.variants.find(v => v.id === variantId)?.modelName ?? null;
    return {
      brand: raw.brand || null,
      modelId,
      modelName,
      variantId,
      variantName,
      engineType: raw.engineType || null,
      transmission: raw.transmission || null,
      year: raw.year ? Number(raw.year) : null,
    };
  }
}
