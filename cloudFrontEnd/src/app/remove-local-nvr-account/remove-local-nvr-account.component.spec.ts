import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RemoveLocalNvrAccountComponent } from './remove-local-nvr-account.component';

describe('REmoveLocalNVRComponent', () => {
  let component: RemoveLocalNvrAccountComponent;
  let fixture: ComponentFixture<RemoveLocalNvrAccountComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ RemoveLocalNvrAccountComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(RemoveLocalNvrAccountComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
