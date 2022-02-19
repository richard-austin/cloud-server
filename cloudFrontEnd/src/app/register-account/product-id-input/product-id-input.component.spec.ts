import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProductIdInputComponent } from './product-id-input.component';

describe('ProductIdInputComponent', () => {
  let component: ProductIdInputComponent;
  let fixture: ComponentFixture<ProductIdInputComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ ProductIdInputComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(ProductIdInputComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
