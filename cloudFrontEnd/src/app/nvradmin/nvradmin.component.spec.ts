import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NVRAdminComponent } from './nvradmin.component';

describe('NVRAdminComponent', () => {
  let component: NVRAdminComponent;
  let fixture: ComponentFixture<NVRAdminComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ NVRAdminComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(NVRAdminComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
