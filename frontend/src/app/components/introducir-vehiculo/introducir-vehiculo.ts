import { Component, OnInit, OnDestroy, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Subject, takeUntil } from 'rxjs';
import { VehicleApiService } from '../../services/vehicle-api.service';
import {
  VehicleModelSummary,
  VehicleVariant,
  VehicleSearchContext,
  EngineType,
  TransmissionType,
} from '../../services/api.models';
import { SelectorMarcaModelo } from '../selector-marca-modelo/selector-marca-modelo';
import { PrecisionBusqueda } from '../precision-busqueda/precision-busqueda';
import { DetalleVehiculo, DetalleVehiculoValue, EnumOption } from '../detalle-vehiculo/detalle-vehiculo';

@Component({
  selector: 'app-introducir-vehiculo',
  standalone: true,
  imports: [CommonModule, SelectorMarcaModelo, PrecisionBusqueda, DetalleVehiculo],
  templateUrl: './introducir-vehiculo.html',
  styleUrl: './introducir-vehiculo.css',
})
export class IntroducirVehiculo implements OnInit, OnDestroy {
  @Input() tieneProblema = false;
  @Output() vehicleContextChange = new EventEmitter<VehicleSearchContext>();
  @Output() enviar = new EventEmitter<void>();

  brands: string[] = [];
  models: VehicleModelSummary[] = [];
  variants: VehicleVariant[] = [];

  loadingBrands = false;
  loadingModels = false;
  loadingVariants = false;

  selectedBrand: string | null = null;
  selectedModelId: number | null = null;
  detailValue: DetalleVehiculoValue = { variantId: null, year: null, engineType: null, transmission: null };

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

  private readonly precisionLabels = [
    'Sin información del vehículo',
    'Orientación muy básica',
    'Diagnóstico aproximado',
    'Diagnóstico probable',
    'Diagnóstico de precisión',
  ];

  // Pesos diagnósticos (suman 100). Ordenados por valor para el mecánico:
  // engineType > year > model > variant > transmission > brand
  private readonly pw = {
    engineType:   30, // cambia todo el árbol de fallos (eléctrico ≠ diésel ≠ gasolina)
    year:         25, // generación mecánica + defectos conocidos/recalls
    model:        20, // plataforma: chasis, suspensión, arquitectura eléctrica
    variant:      12, // código de motor exacto; clave para piezas y TSBs
    transmission:  8, // relevante principalmente en síntomas de tren de transmisión
    brand:         5, // útil solo para peculiaridades de fabricante sin el modelo
  };

  private destroy$ = new Subject<void>();

  constructor(private vehicleApi: VehicleApiService) {}
  ngOnInit(): void {
    this.loadBrands();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  get detailDisabled(): boolean {
    return !this.selectedModelId;
  }

  get canSubmit(): boolean {
    return this.tieneProblema && !!this.selectedBrand && !!this.selectedModelId;
  }

  get precisionLevel(): number {
    let s = 0;
    if (this.selectedBrand)            s += this.pw.brand;
    if (this.selectedModelId)          s += this.pw.model;
    if (this.detailValue.engineType)   s += this.pw.engineType;
    if (this.detailValue.year)         s += this.pw.year;
    if (this.detailValue.variantId)    s += this.pw.variant;
    if (this.detailValue.transmission) s += this.pw.transmission;
    if (s === 0)  return 0;
    if (s <= 20)  return 1;
    if (s <= 50)  return 2;
    if (s <= 80)  return 3;
    return 4;
  }

  get precisionLabel(): string {
    return this.precisionLabels[this.precisionLevel];
  }

  onBrandChange(brand: string | null): void {
    this.selectedBrand = brand;
    this.models = [];
    this.variants = [];
    this.selectedModelId = null;

    if (brand) {
      this.loadingModels = true;
      this.vehicleApi.getModels(brand).pipe(takeUntil(this.destroy$)).subscribe({
        next: models => { this.models = models; this.loadingModels = false; },
        error: () => { this.loadingModels = false; },
      });
    }
    this.emitContext();
  }

  onModelChange(modelId: number | null): void {
    this.selectedModelId = modelId;
    this.variants = [];

    if (modelId) {
      this.loadingVariants = true;
      this.vehicleApi.getVariants(modelId).pipe(takeUntil(this.destroy$)).subscribe({
        next: variants => { this.variants = variants; this.loadingVariants = false; },
        error: () => { this.loadingVariants = false; },
      });
    }
    this.emitContext();
  }

  onDetailChange(value: DetalleVehiculoValue): void {
    this.detailValue = value;
    this.emitContext();
  }

private loadBrands(): void {

  this.loadingBrands = true;

  queueMicrotask(() => {

    this.vehicleApi
      .getBrands()
      .pipe(takeUntil(this.destroy$))
      .subscribe({

        next: brands => {

          this.brands = brands;

          setTimeout(() => {
            this.loadingBrands = false;
          });
        },

        error: () => {

          setTimeout(() => {
            this.loadingBrands = false;
          });
        },
      });

  });
}

  private emitContext(): void {
    const modelName = this.models.find(m => m.id === this.selectedModelId)?.name ?? null;
    const variantName = this.variants.find(v => v.id === this.detailValue.variantId)?.modelName ?? null;
    this.vehicleContextChange.emit({
      brand: this.selectedBrand,
      modelId: this.selectedModelId,
      modelName,
      variantId: this.detailValue.variantId,
      variantName,
      engineType: this.detailValue.engineType,
      transmission: this.detailValue.transmission,
      year: this.detailValue.year,
    });
  }
}
